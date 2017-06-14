package agora.integration

import akka.http.scaladsl.model.ContentTypes
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import agora.domain.io.Sources
import agora.rest.multipart.{MultipartBuilder, MultipartInfo}

import scala.language.reflectiveCalls

trait WorkerIntegrationTest { self: BaseIntegrationTest =>

  import agora.api.Implicits._

  "handleMultipart" should {
    "work end-to-end" in {

      // add a handler which just echos the input multipart byte stream
      worker.service.usingSubscription(_.withPath("basic")).addMultipartHandler { ctxt =>
        ctxt.mapMultipart {
          case (MultipartInfo(key, _, _), sourceFromRequest) =>
            ctxt.completeWithSource(sourceFromRequest, ContentTypes.`text/plain(UTF-8)`)
        }
      }

      // have the client send a multipart requst of bytes
      val (_, workerResponseFuture) = exchangeClient.enqueueAndDispatch("some small job".asJob.matching("path".equalTo("basic").asMatcher)) { worker =>
        val src = Source.fromIterator { () =>
          Iterator
            .from(1)
            .map { x =>
              ByteString(x.toString)
            }
            .take(10)
        }

        val len = Sources.sizeOf(src).futureValue
        val fd  = MultipartBuilder().fromSource("ints", len, src).formData.futureValue
        worker.sendMultipart(fd)
      }

      // validate the response is just what we sent
      val resp          = workerResponseFuture.futureValue
      val Seq(readBack) = resp.sourceResponse.map(_.utf8String).runWith(Sink.seq).futureValue
      readBack shouldBe "12345678910"
    }

    "handle large uploads and results" in {

      // add a handler which just echos the input multipart byte stream
      worker.service.usingSubscription(_.withPath("largeuploads").matchingSubmission("topic".equalTo("biguns").asMatcher)).addMultipartHandler { ctxt =>
        ctxt.mapMultipart {
          case (MultipartInfo(key, _, _), sourceFromRequest) =>
            ctxt.completeWithSource(sourceFromRequest, ContentTypes.`text/plain(UTF-8)`)
        }
      }

      def numbers =
        Iterator
          .from(1)
          .map { x =>
            ByteString(x.toString)
          }
          .take(1000)

      // have the client send a multipart request of bytes
      val job = "doesn't matter".asJob.add("topic" -> "biguns").matching("path".equalTo("largeuploads").asMatcher)
      val (_, workerResponseFuture) = exchangeClient.enqueueAndDispatch(job) { worker =>
        val src = Source.fromIterator { () =>
          numbers
        }
        val len = Sources.sizeOf(src).futureValue
        val fd  = MultipartBuilder().fromSource("ints", len, src).formData.futureValue
        worker.sendMultipart(fd)
      }

      // validate the response is just what we sent
      val resp          = workerResponseFuture.futureValue
      val Seq(readBack) = resp.sourceResponse.map(_.utf8String).runWith(Sink.seq).futureValue
      readBack shouldBe numbers.map(_.utf8String).mkString("")
    }
  }
}
