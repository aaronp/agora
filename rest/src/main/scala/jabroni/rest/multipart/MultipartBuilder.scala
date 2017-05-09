package jabroni.rest.multipart

import java.nio.file.{Files, Path}

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}

class MultipartBuilder {

  private var parts = List[Multipart.FormData.BodyPart]()

  def file(key: String, fileName: String, entity: HttpEntity.Strict): MultipartBuilder = {
    val part: Multipart.FormData.BodyPart = Multipart.FormData.BodyPart.Strict(
      key,
      entity,
      Map("filename" -> fileName))
    add(part)
  }

  def add(part: Multipart.FormData.BodyPart) = {
    parts = part :: parts
    this
  }

  def upload(filePath: Path, chunkSize: Int = 1000) = {
    val src: Source[ByteString, Future[IOResult]] = FileIO.fromPath(filePath, chunkSize)
    val size = Files.size(filePath)
    source(filePath.toFile.getName, size, src)
  }

  def source(fileName: String,
             contentLength: Long,
             data: Source[ByteString, Any],
             contentType: ContentType = ContentTypes.`application/octet-stream`) = {
    val part = Multipart.FormData.BodyPart(fileName, _entity = HttpEntity(contentType, contentLength, data))
    add(part)
  }

  def json[T: Encoder](value: T, key: String = "json") = {
    val jsonString = implicitly[Encoder[T]].apply(value)
    val jsonPart = Multipart.FormData.BodyPart.Strict(
      key,
      HttpEntity(ContentTypes.`application/json`, jsonString.noSpaces))

    add(jsonPart)
  }

  def multipart: Multipart = Multipart.FormData(parts: _*)

  def request(implicit ec : ExecutionContext): Future[MessageEntity] = Marshal(multipart).to[RequestEntity]

}

object MultipartBuilder {
  def apply(): MultipartBuilder = new MultipartBuilder

  //
  //  object implicits {
  //
  //    implicit final def toResponseMarshaller[A](implicit ec: ExecutionContext): ToResponseMarshaller[Source[String, A]] =
  //      Marshaller.withFixedContentType(ContentTypes.`text/plain(UTF-8)`) { messages =>
  //        val data = messages.map(ByteString(_))
  //        HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, data))
  //      }
  //
  //  }
}
