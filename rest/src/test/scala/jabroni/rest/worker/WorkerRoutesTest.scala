package jabroni.rest
package worker

import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.circe.generic.auto._
import jabroni.api._
import jabroni.api.`match`.MatchDetails
import jabroni.domain.IterableSubscriber
import jabroni.domain.io.Sources
import jabroni.rest.multipart.{MultipartBuilder, MultipartPieces}
import jabroni.rest.test.TestUtils._

class WorkerRoutesTest extends BaseRoutesSpec {

  import WorkerRoutesTest._

  "WorkerRoutes" should {

    "be able to upload from strict multipart requests" in {
      val wr = WorkerRoutes()

      wr.addMultipartHandler { workContext =>
        val Some(upload) = workContext.multipartUpload("some.file")

        val subscriber = new IterableSubscriber[ByteString]()
        upload.runWith(Sink.fromSubscriber(subscriber))
        val lines = subscriber.iterator.map(_.utf8String).toList
        val e: ResponseEntity = lines.mkString("\n")
        val resp = HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
        workContext.complete(resp)
      }(wr.defaultSubscription.withPath("uploadTest"))


      val expectedContent = "It was the best of times\nIt was the worst of times\nAs I write this, Trump just fired James Comey\n"
      val upload = {
        Multipart.FormData(Multipart.FormData.BodyPart.Strict(
          "csv",
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, expectedContent),
          Map("filename" -> "some.file")))
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

      wr.addMultipartHandler { (workContext: WorkContext[MultipartPieces]) =>
        val Some(upload) = workContext.multipartUpload("some.file")

        val subscriber = new IterableSubscriber[ByteString]()
        upload.runWith(Sink.fromSubscriber(subscriber))
        val lines = subscriber.iterator.map(_.utf8String).toList
        val e: ResponseEntity = lines.mkString("\n")
        val resp = HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
        workContext.complete(resp)
      }(wr.defaultSubscription.withPath("uploadTest"))


      val expectedContent =
        """here
          |is
          |a
          |bunch of
          |content
        """.stripMargin

      def upload = {
        val bytes = Sources.asBytes(Source.single(expectedContent), "")
        val len = Sources.sizeOf(bytes).futureValue

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
    "be able to upload files created using fromPath" in {
      val wr = WorkerRoutes()

      withTmpFile("worker-routes-upload") { uploadFile =>
        withTmpFile("worker-routes-download") { downloadFile =>

          val expectedContent =
            """I like this:
              |https://twitter.com/shitmydadsays/status/862102283638587392
              |
              |It's more likely that Trump fire Comey 'cause
              |Trump knows he's done loads of worse things the
              |FBI hasn't even seen yet, and so Trumps's thinking
              |the FBI must be feckless if he can get away
              |with all that other stuff...""".stripMargin

          uploadFile.text = expectedContent

          wr.addMultipartHandler { ctxt =>
            ctxt.multipartSavedTo("james.comey", downloadFile).block
            ctxt.complete {
              HttpResponse(entity = (downloadFile.toAbsolutePath.toString))
            }
          }(wr.defaultSubscription.withPath("uploadTest"))

          val upload = MultipartBuilder().fromPath(uploadFile, fileName = "james.comey").formData.futureValue
          val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

          httpRequest ~> wr.routes ~> check {
            status shouldEqual StatusCodes.OK
            val uploadPath = response.entity.toStrict(testTimeout).block.data.utf8String
            uploadPath shouldBe downloadFile.toAbsolutePath.toString

            downloadFile.text shouldBe expectedContent
          }
        }
      }
    }
    "handle dynamically created routes" in {

      val bytes = ByteString("Testing 123")
      val request = MultipartBuilder().
        json("first", SomeData("hello", 654)).
        json("second", SomeData("more", 111)).
        json("third", SomeData("again", 8)).
        text("key1", "2,3,5\n4,5,6").formData.futureValue


      val httpReq: HttpRequest = WorkerClient.multipartRequest("doit", matchDetails, request)

      val wr = WorkerRoutes()

      // add a handler for 'doit'
      wr.addMultipartHandler { ctxt =>

        val first = ctxt.multipartText("first").block
        first shouldBe """{"foo":"hello","bar":654}"""
        ctxt.multipartText("second").block shouldBe """{"foo":"more","bar":111}"""
        ctxt.multipartText("third").block shouldBe """{"foo":"again","bar":8}"""
        ctxt.multipartText("key1").block shouldBe "2,3,5\n4,5,6"

        ctxt.complete(HttpResponse(entity = first))
      }(wr.defaultSubscription.withPath("doit"))

      httpReq ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}

object WorkerRoutesTest {

  case class SomeData(foo: String, bar: Int)

  val matchDetails = MatchDetails(nextMatchId(), nextSubscriptionKey(), nextJobId(), 3, 1234567)


}