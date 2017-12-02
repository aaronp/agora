package agora.rest.worker

import agora.api.exchange.{AsClient, SubmitJob}
import agora.io.{IterableSubscriber, Sources}
import agora.rest.integration.BaseIntegrationTest
import agora.rest.multipart.{MultipartBuilder, MultipartInfo}
import akka.http.scaladsl.model.{ContentTypes, HttpResponse, Multipart}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.language.reflectiveCalls

trait WorkerIntegrationSpec extends FailFastCirceSupport { self: BaseIntegrationTest =>

  import agora.api.Implicits._
  import agora.rest.implicits._

  "handleMultipart" should {
    "work end-to-end" in {

      // add a handler which just echos the input multipart byte stream
      worker.service.usingSubscription(_.withPath("basic")).addHandler[Multipart.FormData] { ctxt =>
        ctxt.mapMultipart {
          case (MultipartInfo(key, _, _), sourceFromRequest) =>
            ctxt.completeWithSource(sourceFromRequest, ContentTypes.`text/plain(UTF-8)`)
        }
      }

      // have the client send a multipart request of bytes
      val job: SubmitJob = "some small job".asJob.matching("path".equalTo("basic").asMatcher())

      val dispatchInts: AsClient[SubmitJob, HttpResponse] = AsClient.lift[SubmitJob, HttpResponse] { dispatch =>
        val src = Source.fromIterator { () =>
          Iterator
            .from(1)
            .map { x =>
              ByteString(x.toString)
            }
            .take(10)
        }
        val len = Sources.sizeOf(src).futureValue
        val fd =
          MultipartBuilder().fromStrictSource("ints", len, src, ContentTypes.`application/json`).formData.futureValue
        val client = WorkerClient(worker.conf.clientConfig, dispatch)

        client.sendRequest(fd)
      }

      implicit val strResp: AsClient[SubmitJob, String] = dispatchInts.map { resp =>
        IterableSubscriber.iterate(resp.entity.dataBytes, 1000, true).mkString("")
      }

      val textResponse: Future[String] = exchangeClient.enqueue(job)
      val readBack                     = textResponse.futureValue
      readBack shouldBe "12345678910"
    }

    "handle large uploads and results" in {

      // add a handler which just echos the input multipart byte stream
      worker.service
        .usingSubscription(_.withPath("largeuploads").matchingSubmission("topic".equalTo("biguns").asMatcher()))
        .addHandler[Multipart.FormData] { ctxt =>
          ctxt.mapMultipart {
            case (MultipartInfo(key, _, _), sourceFromRequest) =>
              ctxt.completeWithSource(sourceFromRequest, ContentTypes.`text/plain(UTF-8)`)
          }
        }

      def numbers =
        Iterator
          .from(1)
          .map { x =>
            ByteString(s"$x\n")
          }
          .take(1000)

      val DispatchBiguns: AsClient[SubmitJob, HttpResponse] = AsClient.lift[SubmitJob, HttpResponse] { dispatch =>
        val src = Source.fromIterator { () =>
          numbers
        }
        val len = Sources.sizeOf(src).futureValue
        val fd  = MultipartBuilder().fromStrictSource("ints", len, src).formData.futureValue

        WorkerClient(worker.conf.clientConfig, dispatch).sendRequest(fd)
      }

      // have the client send a multipart request of bytes
      val job = "doesn't matter".asJob.add("topic" -> "biguns").matching("path".equalTo("largeuploads").asMatcher())

      implicit val replyWithStrings: AsClient[SubmitJob, String] =
        asRichAsClientHttpResponse(DispatchBiguns).iterate().map(_.mkString("", "\n", "\n"))

      val readBack: Future[String] = exchangeClient.enqueue(job)
      readBack.futureValue shouldBe numbers.map(_.utf8String).mkString("")
    }
  }
}
