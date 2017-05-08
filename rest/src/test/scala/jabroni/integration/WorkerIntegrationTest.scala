package jabroni.integration

import akka.NotUsed
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.stream.scaladsl.{Framing, Sink, Source}
import akka.util.ByteString
import jabroni.api.exchange.WorkSubscription
import jabroni.domain.io.Sources
import jabroni.rest.multipart.MultipartBuilder

import language.reflectiveCalls

class WorkerIntegrationTest extends BaseIntegrationTest {

  import jabroni.api.Implicits._

  "handleMultipart" should {
    "work end-to-end" in {

      // add a handler which just echos the input multipart byte stream
      worker.service.handleMultipart { ctxt =>
        val (_, sourceFromRequest) = ctxt.request.head
        HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, sourceFromRequest))
      }(worker.service.defaultSubscription.withPath("small"))

      // have the client send a multipart requst of bytes
      val (_, workerResponseFuture) = exchangeClient.enqueueAndDispatch("doesn't matter".asJob) { worker =>
        val src = Source.fromIterator { () =>
          Iterator.from(1).map { x =>
            ByteString(x.toString)
          }.take(10)
        }

        val len = Sources.sizeOf(src).futureValue
        val fd = MultipartBuilder().fromSource("ints", len, src).formData.futureValue
        worker.sendMultipart(fd)
      }

      // validate the response is just what we sent
      val resp = workerResponseFuture.futureValue
      val Seq(readBack) = resp.sourceResponse.map(_.utf8String).runWith(Sink.seq).futureValue
      readBack shouldBe "12345678910"
    }

    "handle large uploads and results" in {

      // add a handler which just echos the input multipart byte stream
      worker.service.handleMultipart { ctxt =>
        val (_, sourceFromRequest) = ctxt.request.head
        HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, sourceFromRequest))
      }(worker.service.defaultSubscription.withPath("large"))

      // have the client send a multipart requst of bytes
      val (_, workerResponseFuture) = exchangeClient.enqueueAndDispatch("doesn't matter".asJob) { worker =>
        val src = Source.fromIterator { () =>
          Iterator.from(1).map { x =>
            ByteString(x.toString)
          }.take(1000)
        }
        val len = Sources.sizeOf(src).futureValue
        val fd = MultipartBuilder().fromSource("ints", len, src).formData.futureValue
        worker.sendMultipart(fd)
      }

      // validate the response is just what we sent
      val resp = workerResponseFuture.futureValue
      val Seq(readBack) = resp.sourceResponse.map(_.utf8String).runWith(Sink.seq).futureValue
      println(readBack)
    }
  }
}
