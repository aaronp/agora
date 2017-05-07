package jabroni.rest

import java.nio.charset.StandardCharsets

import akka.NotUsed
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.stream.scaladsl.{Framing, Source}
import akka.util.ByteString
import io.circe.Json

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

object MultipartRequestSupport extends MultipartRequestSupport {
  case class FileUpload(name : String, source : Source[ByteString, NotUsed])
}

trait MultipartRequestSupport {

  import MultipartRequestSupport._

  def asRequestEntity(jsonPartName: String, data: Json, files: Seq[FileUpload], timeout: FiniteDuration)
                     (implicit execContext: ExecutionContext, materializer: Materializer): Future[RequestEntity] = {
    import Multipart.FormData.BodyPart._
    val uploadEntities = files.map {
      case FileUpload(name, source) =>
        val bytesFuture: Future[ByteString] = source.runFold(ByteString.empty)(_ ++ _)
        val bytes = Await.result(bytesFuture, timeout)
        Strict("file",
          HttpEntity(MediaTypes.`application/octet-stream`, bytes),
          Map("filename" -> name))
    }

    val jsonBodyEntity: Multipart.FormData.BodyPart = {
      Strict(jsonPartName, HttpEntity(MediaTypes.`application/json`, data.toString), Map.empty)
    }

    partsAsRequestEntity((jsonBodyEntity +: uploadEntities): _*)
  }

  private def partsAsRequestEntity(parts: Multipart.FormData.BodyPart*)(implicit execContext: ExecutionContext, materializer: Materializer): Future[RequestEntity] = {
    val formData = Multipart.FormData(parts: _*)
    Marshal(formData).to[RequestEntity]
  }


  def streamResponse(response: HttpResponse, frameLen: Int, sizeLimit: Option[Long] = None, allowTruncation : Boolean = true): Source[String, Any] = {
    val raw: Source[ByteString, Any] = sizeLimit.fold(response.entity)(response.entity.withSizeLimit).dataBytes
    streamBytes(raw, frameLen, allowTruncation)
  }

  def streamBytes[T](raw: Source[ByteString, T], frameLen: Int, allowTruncation : Boolean): Source[String, T] = {
    raw.
      //      via(Compression.gunzip()).
      via(Framing.delimiter(ByteString("\n"), maximumFrameLength = frameLen, allowTruncation = allowTruncation)).
      map(_.decodeString(StandardCharsets.UTF_8))
  }

}