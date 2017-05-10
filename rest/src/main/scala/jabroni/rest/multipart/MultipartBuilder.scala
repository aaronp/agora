package jabroni.rest
package multipart

import java.nio.file.{Files, Path}

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model._
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import io.circe.Encoder

import scala.concurrent.{ExecutionContext, Future}

class MultipartBuilder {

  private var partsList = List[Multipart.FormData.BodyPart]()

  def parts = partsList

  def file(key: String, file: Path, chunkSize: Int = 1024): MultipartBuilder = {
    val src: Source[ByteString, Future[IOResult]] = FileIO.fromPath(file, chunkSize)
    val len = Files.size(file)
    val entity = HttpEntity(ContentTypes.`application/octet-stream`, len, src)
    val part = Multipart.FormData.BodyPart(key, entity)
    add(part)
  }

  def file(key: String, fileName: String, entity: HttpEntity.Strict): MultipartBuilder = {
    val part: Multipart.FormData.BodyPart = Multipart.FormData.BodyPart.Strict(
      key,
      entity,
      Map("filename" -> fileName))
    add(part)
  }

  def upload(filePath: Path, chunkSize: Int = 1000) = {
    val src: Source[ByteString, Future[IOResult]] = FileIO.fromPath(filePath, chunkSize)
    val size = Files.size(filePath)
    source(filePath.toFile.getName, size, src)
  }

  def source(fileName: String,
             contentLength: Long,
             data: Source[ByteString, Any],
             contentType: ContentType = ContentTypes.`application/octet-stream`): MultipartBuilder = {
    val entity = HttpEntity(contentType, contentLength, data)
    val part: FormData.BodyPart = Multipart.FormData.BodyPart(fileName, _entity = entity, Map("filename" -> fileName))
    add(part)
  }

  def sourceWithComputedSize(fileName: String,
                             data: Source[ByteString, Any],
                             contentType: ContentType = ContentTypes.`application/octet-stream`)(implicit materializer: akka.stream.Materializer): Future[MultipartBuilder] = {
    import materializer._
    sizeOf(data).map { size =>
      source(fileName, size, data, contentType)
    }
  }

  def sizeOf(src: Source[ByteString, Any])(implicit materializer: akka.stream.Materializer): Future[Long] = {
    src.map { bs =>
      val len = bs.size.toLong
      val x = bs.decodeString("UTF-8")
      val len2 = x.length

      println(s"$len vs $len2 for $x")
      len2.toLong

    }.runReduce(_ + _)
  }

  def json[T: Encoder](key: String, value: T): MultipartBuilder = {
    val jsonString = implicitly[Encoder[T]].apply(value)
    json(key, jsonString.noSpaces)
  }

  def json(key: String, jsonString: String): MultipartBuilder = text(key, jsonString, ContentTypes.`application/json`)

  def text(key: String, text: String, contentType: ContentType = ContentTypes.`text/plain(UTF-8)`): MultipartBuilder = {
    val textPart = Multipart.FormData.BodyPart.Strict(
      key,
      HttpEntity(contentType, ByteString(text)))
    add(textPart)
  }

  def sourceWithUnknownSize(key: String, data: Source[ByteString, Any], contentType: ContentType = ContentTypes.`text/plain(UTF-8)`) = {
    val entity: BodyPartEntity = IndefiniteLength(contentType, data)
    val jsonPart = Multipart.FormData.BodyPart(
      key,
      _entity = entity,
      Map("filename" -> key))
    add(jsonPart)
  }


  def add(part: Multipart.FormData.BodyPart) = {
    partsList = part :: partsList
    this
  }

  def multipart: Multipart = Multipart.FormData(parts: _*)

  def request(implicit ec: ExecutionContext): Future[MessageEntity] = Marshal(multipart).to[RequestEntity]

}

object MultipartBuilder {
  def apply(): MultipartBuilder = new MultipartBuilder
}
