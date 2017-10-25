package agora.rest
package multipart

import java.nio.file.Path

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{BodyPartEntity, _}
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Encoder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Just exposes convenience methods for putting together multipart requests
  *
  * see http://doc.akka.io/docs/akka-http/10.0.4/scala/http/common/http-model.html
  */
class MultipartBuilder(defaultSourceContentType: ContentType) {

  private var partsList = List[Multipart.FormData.BodyPart]()

  def parts: List[FormData.BodyPart] = partsList

  def fromPath(file: Path, chunkSize: Int = -1, contentType: ContentType = defaultSourceContentType, fileName: String = null): MultipartBuilder = {
    val entity  = HttpEntity.fromPath(contentType, file, chunkSize)
    val fileKey = Option(fileName).orElse(Option(file.getFileName.toString))
    add(fileKey.get, entity, fileKey)
  }

  def fromStrictSource(key: String,
                       length: Long,
                       data: Source[ByteString, Any],
                       contentType: ContentType = defaultSourceContentType,
                       fileName: String = null): MultipartBuilder = {
    val entity: BodyPartEntity = HttpEntity.Default(contentType, length, data)
    add(key, entity, Option(fileName))
  }

  def json[T: Encoder: ClassTag](value: T): MultipartBuilder = {
    val key = implicitly[ClassTag[T]].runtimeClass.getName
    json(key, value)
  }

  def json[T: Encoder](key: String, value: T): MultipartBuilder = {
    jsonString(key, implicitly[Encoder[T]].apply(value).noSpaces)
  }

  def jsonString(key: String, jsonString: String): MultipartBuilder = text(key, jsonString, `application/json`)

  def text(key: String, text: String, contentType: ContentType = `text/plain(UTF-8)`): MultipartBuilder = {
    val textPart = Multipart.FormData.BodyPart.Strict(key, HttpEntity(contentType, ByteString(text)))
    add(textPart)
  }

  def add(key: String, e: BodyPartEntity, fileName: Option[String]): MultipartBuilder = {
    add(Multipart.FormData.BodyPart(key, _entity = e, fileName.map("filename" -> _).toMap))
  }

  def add(part: Multipart.FormData.BodyPart): MultipartBuilder = {
    partsList = part :: partsList
    this
  }

  private def asFormData: Multipart.FormData = Multipart.FormData(parts: _*)

  def formData(implicit mat: Materializer, timeout: FiniteDuration): Future[FormData.Strict] = {
    asFormData.toStrict(timeout)
  }

}

object MultipartBuilder {
  def apply(defaultSourceContentType: ContentType = `text/plain(UTF-8)`): MultipartBuilder =
    new MultipartBuilder(defaultSourceContentType)

}
