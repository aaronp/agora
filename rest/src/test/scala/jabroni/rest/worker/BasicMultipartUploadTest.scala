package jabroni.rest.worker

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.Multipart.FormData.BodyPart
import akka.http.scaladsl.model.{BodyPartEntity, _}
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{Framing, Sink, Source}
import akka.util.ByteString
import jabroni.domain.IterableSubscriber
import jabroni.domain.io.Sources
import jabroni.rest.{BaseRoutesSpec, BaseSpec}
import jabroni.rest.multipart.{MultipartBuilder, MultipartDirectives, MultipartPieces}

import scala.concurrent.Future
import scala.language.reflectiveCalls


/**
  * http://doc.akka.io/docs/akka-http/10.0.3/scala/http/routing-dsl/directives/file-upload-directives/fileUpload.html
  */
class BasicMultipartUploadTest extends BaseRoutesSpec with MultipartDirectives {

  import jabroni.domain.io.implicits._

  "Just big" should {
    "handle unknown lengths" in {

      val route =
        extractRequestContext { ctx =>
          implicit val materializer = ctx.materializer
          implicit val ec = ctx.executionContext

          multipartData { (sourcesByKey: MultipartPieces) =>
            //          fileUpload("csv") {
            //            case (metadata, byteSource) =>


            val bs = sourcesByKey.collectFirst {
              case (metadata, byteSource) if metadata.fieldName == "ints" => byteSource
            }

            val byteSource = bs.getOrElse(sys.error(s"Couldn't find csv in ${sourcesByKey.keySet}"))

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


      val ints: Source[Int, NotUsed] = Source.unfold(1) {
        case 1000 => None
        case i => Some(i + 1, i + 1)
      }
      val bytes = ints.map(_.toString).map { s => ByteString(s"$s\n") }
      val len = Sources.sizeOf(bytes).futureValue

      val fd = MultipartBuilder().fromSource("ints", len, bytes).formData.futureValue

      Post("/", fd) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldEqual "Sum: 500499"
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

            val byteSource = bs.getOrElse(sys.error(s"couldn't find 'csv' in ${sourcesByKey.keySet}"))

            val sumF: Future[Int] =
            // sum the numbers as they arrive so that we can
            // accept any size of file
              byteSource.via(Framing.delimiter(ByteString("\n"), 1024, allowTruncation = true))
                .mapConcat(_.utf8String)
                .runFold(0) { (acc, _) => acc + 1 }

            onSuccess(sumF) { sum => complete(s"Sum: $sum") }
          }
        }

      import jabroni.rest.test.TestUtils._

      withTmpFile("multipart-upload") { uploadFile =>

        // tests:
        uploadFile.text = "hello world!"
        val upload = MultipartBuilder().fromPath(uploadFile, fileName = "csv").formData.futureValue

        Post("/", upload) ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual s"Sum: ${"hello world!".length}"
        }
      }

    }
  }
}
