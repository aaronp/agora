package agora.rest
package worker

import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.circe.generic.auto._
import io.circe.syntax._
import agora.api._
import agora.api.`match`.MatchDetails
import agora.domain.IterableSubscriber
import agora.domain.io.Sources
import agora.health.HealthDto
import agora.rest.multipart.{MultipartBuilder, MultipartInfo}

import scala.concurrent.Future

class WorkerRoutesTest extends BaseRoutesSpec {

  import WorkerRoutesTest._

  "WorkerRoutes.health" should {
    "reply w/ the worker health" in {

      WorkerHttp.healthRequest ~> WorkerRoutes().routes ~> check {
        status shouldEqual StatusCodes.OK
        val dto = responseAs[HealthDto]
        dto.objectPendingFinalizationCount should be >= 0
        contentType shouldBe ContentTypes.`application/json`
      }
    }
  }
  "WorkerRoutes" should {

    "be able to upload from strict multipart requests" in {
      val wr = WorkerRoutes()

      wr.usingSubscription(_.withPath("uploadTest")).addHandler[Multipart.FormData] { workContext =>
        val respFuture: Future[HttpResponse] = workContext.mapFirstMultipart {
          case (MultipartInfo(_, Some("some.file"), _), upload) =>
            val subscriber = new IterableSubscriber[ByteString]()
            upload.runWith(Sink.fromSubscriber(subscriber))
            val lines             = subscriber.iterator.map(_.utf8String).toList
            val e: ResponseEntity = lines.mkString("\n")
            HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
        }

        workContext.completeWith(respFuture)
      }

      val expectedContent = "It was the best of times\nIt was the worst of times\nAs I write this, Trump just fired James Comey\n"
      val upload = {
        Multipart.FormData(Multipart.FormData.BodyPart.Strict("csv", HttpEntity(ContentTypes.`text/plain(UTF-8)`, expectedContent), Map("filename" -> "some.file")))
      }

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val text = response.entity.toStrict(testTimeout).block.data.utf8String
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        text shouldBe expectedContent
      }
    }

    "be able to upload from indefinite multipart requests" in {
      val wr = WorkerRoutes()

      wr.usingSubscription(_.withPath("uploadTest")).addHandler { (ctxt: WorkContext[Multipart.FormData]) =>
        ctxt.foreachMultipart {
          case (MultipartInfo("some.file", file, _), upload) =>
            val lines             = IterableSubscriber.iterate(upload, 100, true)
            val e: ResponseEntity = lines.mkString("\n")
            ctxt.complete {
              HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
            }
        }
      }

      val expectedContent =
        """here
          |is
          |a
          |bunch of
          |content
        """.stripMargin

      def upload = {
        val bytes = Sources.asBytes(Source.single(expectedContent), "")
        val len   = Sources.sizeOf(bytes).futureValue

        MultipartBuilder().fromSource("some.file", len, bytes, fileName = "some.file").formData.futureValue
      }

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val text = response.entity.toStrict(testTimeout).block.data.utf8String
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        text shouldBe expectedContent
      }
    }
    "handle dynamically created routes" in {

      val request = MultipartBuilder()
        .json("first", SomeData("hello", 654))
        .json("second", SomeData("more", 111))
        .json("third", SomeData("again", 8))
        .text("key1", "2,3,5\n4,5,6")
        .formData
        .futureValue

      val httpReq: HttpRequest = WorkerClient.multipartRequest("doit", matchDetails, request)

      val wr = WorkerRoutes()

      // add a handler for 'doit'
      wr.usingSubscription(_.withPath("doit")).addHandler[Multipart.FormData] { ctxt =>
        val textByKeyFuture = ctxt.flatMapMultipart {
          case (MultipartInfo(key, _, _), src) => Sources.asText(src).map(text => (key, text))
        }

        textByKeyFuture.foreach { pears =>
          ctxt.completeWithJson {
            pears.toMap.asJson
          }
        }
      }

      httpReq ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val textByKey = responseAs[Map[String, String]]
        textByKey shouldBe Map(
          "first"  -> "{\"foo\":\"hello\",\"bar\":654}",
          "second" -> "{\"foo\":\"more\",\"bar\":111}",
          "third"  -> "{\"foo\":\"again\",\"bar\":8}",
          "key1"   -> "2,3,5\n4,5,6"
        )
      }
    }
  }
}

object WorkerRoutesTest {

  case class SomeData(foo: String, bar: Int)

  val matchDetails = MatchDetails(nextMatchId(), nextSubscriptionKey(), nextJobId(), 3, 1234567)

}
