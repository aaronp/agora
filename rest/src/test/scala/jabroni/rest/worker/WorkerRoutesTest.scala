package jabroni.rest
package worker

import java.nio.file.{Files, Path, Paths, StandardOpenOption}

import akka.Done
import akka.http.scaladsl.model._
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import io.circe.Decoder.Result
import io.circe.Json
import io.circe.generic.auto._
import jabroni.api._
import jabroni.api.`match`.MatchDetails
import jabroni.domain.IterableSubscriber
import jabroni.rest.multipart.MultipartBuilder

import scala.concurrent.Future
import scala.language.reflectiveCalls

class WorkerRoutesTest extends BaseSpec {

  import WorkerRoutesTest._

  "WorkerRoutes" should {

    "be able to upload from strict multipart requests" in {
      val wr = WorkerRoutes()

      wr.handleMultipart { workContext =>
        val Some(upload) = workContext.multipartUpload("some.file")

        val subscriber = new IterableSubscriber[ByteString]()
        upload.runWith(Sink.fromSubscriber(subscriber))
        val lines = subscriber.iterator.map(_.utf8String).toList
        val e: ResponseEntity = lines.mkString("\n")
        HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
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

    "be able to upload from indefinite multipart requests" ignore {
      val wr = WorkerRoutes()

      wr.handleMultipart { workContext =>
        val Some(upload) = workContext.multipartUpload("some.file")

        val subscriber = new IterableSubscriber[ByteString]()
        upload.runWith(Sink.fromSubscriber(subscriber))
        val lines = subscriber.iterator.map(_.utf8String).toList

        workContext.take(1)
        val e: ResponseEntity = lines.mkString("\n")
        HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
      }(wr.defaultSubscription.withPath("uploadTest"))


      def upload = {
        val content = Source.single(ByteString(
          """here
            |is
            |a
            |bunch of
            |content
          """.stripMargin))

        MultipartBuilder().
          sourceWithUnknownSize("some.file", content).multipart
      }

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val text = response.entity.toStrict(testTimeout).block.data.utf8String
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        text shouldBe "2,3,5\n7,11,13,17,23\n29,31,37"
      }
    }
    "be able to upload from sourceWithComputedSize" in {
      val wr = WorkerRoutes()

      withTmpFile { uploadFile =>
        withTmpFile { downloadFile =>

          import scala.collection.JavaConverters._
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

          wr.handleMultipart { ctxt =>
            ctxt.multipartSavedTo("james.comey", downloadFile).block
            ctxt.take(1)
            HttpResponse(entity = (downloadFile.toAbsolutePath.toString))
          }(wr.defaultSubscription.withPath("uploadTest"))

          val upload = MultipartBuilder().file("james.comey", uploadFile).multipart
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
    "handle dynamically created routes" ignore {

      val bytes = ByteString("Testing 123")
      val request = MultipartBuilder().
        json("first", SomeData("hello", 654)).
        json("second", SomeData("more", 111)).
        json("third", SomeData("again", 8)).
        file("key1", "foo.csv", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "2,3,5\n4,5,6")).
        source("bar.csv", bytes.size, Source.single(bytes)).multipart


      val httpReq: HttpRequest = WorkerClient.multipartRequest("doit", matchDetails, request)

      val wr = WorkerRoutes()

      // we should get an error as we don't know how to handle 'doit' yet
      //      httpReq ~> wr.routes ~> check {
      //        //        val x = response
      //        //        println(x.status)
      //        rejection
      //      }

      // add a handler for 'doit'
      wr.handleMultipart { ctxt =>

        val first = ctxt.multipartText("first").block
        first shouldBe """{"foo":"hello","bar":654}"""
        ctxt.multipartText("second").block shouldBe """{"foo":"more","bar":111}"""
        ctxt.multipartText("third").block shouldBe """{"foo":"again","bar":8}"""
        ctxt.multipartText("key1").block shouldBe "2,3,5\n4,5,6"

        val files = ctxt.multipartKeys.flatMap(_.fileName)
        files should contain only("foo.csv", "bar.csv")

        HttpResponse(entity = first)
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

  def withTmpFile[T](f: Path => T) = {
    val tmpFile = Files.createTempFile(getClass.getSimpleName, ".test")
    try {
      f(tmpFile)
    } finally {
      Files.delete(tmpFile)
    }
  }


  implicit class RichPath(val path: Path) extends AnyVal {

    import scala.collection.JavaConverters._

    def text: String = {
      import scala.collection.JavaConverters._
      Files.readAllLines(path).asScala.mkString("\n")
    }

    def text_=(str: String): Unit = {
      val lines = str.lines.toIterable.asJava
      Files.write(path, lines, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }
  }

}