package jabroni.rest.worker

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Framing, Sink}
import akka.util.ByteString
import jabroni.domain.IterableSubscriber
import jabroni.rest.BaseSpec
import jabroni.rest.multipart.{MultipartBuilder, MultipartDirectives, MultipartPieces}

import scala.concurrent.Future
import scala.language.reflectiveCalls


/**
  * http://doc.akka.io/docs/akka-http/10.0.3/scala/http/routing-dsl/directives/file-upload-directives/fileUpload.html
  */
class BasicMultipartUploadTest extends BaseSpec with MultipartDirectives {


  "Just upload" should {
    "upload" in {

      val route =
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer
          implicit val ec = ctx.executionContext

          multipartData { (sourcesByKey: MultipartPieces) =>
            //          fileUpload("csv") {
            //            case (metadata, byteSource) =>


            val bs = sourcesByKey.collectFirst {
              case (metadata, byteSource) if metadata.fieldName == "csv" => byteSource
            }

            val byteSource = bs.get

            val sumF: Future[Int] =
            // sum the numbers as they arrive so that we can
            // accept any size of file
              byteSource.via(Framing.delimiter(ByteString("\n"), 1024))
                .mapConcat(_.utf8String.split(",").toVector)
                .map(_.toInt)
                .runFold(0) { (acc, n) => acc + n }

            onSuccess(sumF) { sum => complete(s"Sum: $sum") }
          }
        }

      // tests:
      val multipartForm: Multipart.FormData.Strict =
        Multipart.FormData(Multipart.FormData.BodyPart.Strict(
          "csv",
          HttpEntity(ContentTypes.`text/plain(UTF-8)`, "2,3,5\n7,11,13,17,23\n29,31,37\n"),
          Map("filename" -> "primes.csv")))

      Post("/", multipartForm) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Sum: 178"
      }

    }
    "upload file" in {

      val route =
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer
          implicit val ec = ctx.executionContext

          multipartData { (sourcesByKey: MultipartPieces) =>

            val bs = sourcesByKey.collectFirst {
              case (metadata, byteSource) if metadata.fieldName == "csv" => byteSource
            }

            val byteSource = bs.get

            val sumF: Future[Int] =
            // sum the numbers as they arrive so that we can
            // accept any size of file
              byteSource.via(Framing.delimiter(ByteString("\n"), 1024))
                .mapConcat(_.utf8String)
                .runFold(0) { (acc, _) => acc + 1 }

            onSuccess(sumF) { sum => complete(s"Sum: $sum") }
          }
        }

      import WorkerRoutesTest._

      withTmpFile { uploadFile =>

        // tests:
        uploadFile.text = "hello world!"
        val upload = MultipartBuilder().file("csv", uploadFile).multipart

        Post("/", upload) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual s"Sum: ${"hello world!".length}"
        }
      }

    }
  }
}
