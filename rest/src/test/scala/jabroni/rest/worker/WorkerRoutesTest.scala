package jabroni.rest
package worker

import java.nio.file.{Files, Path, Paths}

import akka.Done
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import io.circe.Decoder.Result
import io.circe.Json
import io.circe.generic.auto._
import jabroni.api._
import jabroni.api.`match`.MatchDetails
import jabroni.rest.multipart.MultipartBuilder

import scala.concurrent.Future
import scala.language.reflectiveCalls

class WorkerRoutesTest extends BaseSpec {

  import WorkerRoutesTest._

  "WorkerRoutes" should {

    "be able to upload from sourceWithUnknownSize" ignore {
      val wr = WorkerRoutes()

      wr.handleMultipart { ctxt =>

        val keys = ctxt.multipartKeys
        println(keys)
        val Some(upload) = ctxt.multipartUpload("some.file")

        val lines = upload.runWith(Sink.seq).block.map(_.decodeString("UTF-8"))
        lines.foreach(println)

        ctxt.take(1)
        ""
      }(wr.defaultSubscription.withPath("uploadTest"))

      val content = {
        def iter = Iterator.from(1).take(10).map { i =>
          val width = (i % 200) + 1
          val text = width.toString.substring(0, 1) * width
          println("making " + text)
          s"$text\n"
        }

        Source.fromIterator(() => iter.map(ByteString.apply))
      }
      val upload = MultipartBuilder().
        sourceWithUnknownSize("some.file", content).multipart

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
    "be able to upload from sourceWithComputedSize" in {
      val wr = WorkerRoutes()

      withTmpFile { tmpFile =>

        wr.handleMultipart { ctxt =>

          val keys = ctxt.multipartKeys
          println(keys)
          val Some(upload) = ctxt.multipartUpload("some.file")
          //          val fileRes: IOResult = upload.runWith(FileIO.toPath(tmpFile, Set(StandardOpenOption.WRITE))).futureValue
          //          println(s"Wrote to $tmpFile")

          val ssx: Future[Done] = upload.runForeach { bs =>

            println(s" GOT " + bs)

          }
          ssx.block
          val lines = upload.runWith(Sink.seq).block.map(_.decodeString("UTF-8"))
          lines.foreach(println)

          val data: Json = ctxt.multipartJson("someData").block
          val x: Result[Int] = data.hcursor.downField("x").as[Int]
          x shouldBe Right(1)

          ctxt.take(1)
          ""
        }(wr.defaultSubscription.withPath("uploadTest"))

        val path = Paths.get("/tmp/aaron-ffs/1494316521371/some.txt")
        val content: Source[ByteString, Future[IOResult]] = FileIO.fromPath(path)


        val expectedSize = Files.size(path)
        val upload = MultipartBuilder().
          json("someData", """{ "x" : 1 """).
          source("some.file", expectedSize, content).multipart

        val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

        httpRequest ~> wr.routes ~> check {
          status shouldEqual StatusCodes.OK
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

        first
      }(wr.defaultSubscription.withPath("doit"))

      httpReq ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }

  val matchDetails = MatchDetails(nextMatchId(), nextSubscriptionKey(), nextJobId(), 3, 1234567)


  def withTmpFile[T](f: Path => T) = {
    val tmpFile = Files.createTempFile(getClass.getSimpleName, ".test")
    try {
      f(tmpFile)
    } finally {
      Files.delete(tmpFile)
    }
  }
}

object WorkerRoutesTest {

  case class SomeData(foo: String, bar: Int)


}