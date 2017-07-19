package agora.rest.support

import java.nio.file.Path
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.stream.scaladsl.FileIO
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import java.io

import agora.io.implicits._
import akka.stream.Materializer

import scala.concurrent.Future

trait RequestDao {
  type ID

  def writeDown(req: HttpRequest): Future[ID]

  def read(id: ID): Future[HttpRequest]

  def findBetween(before: ZonedDateTime, after: ZonedDateTime): Future[List[ID]]

  def findByUri(text: String): Future[List[ID]]

  def findByBody(text: String): Future[List[ID]]

}

object RequestDao {
  def apply(dir: Path)(implicit mat: Materializer): RequestDao = {
    new FileRequestDao(dir)
  }

  case class SavedRequest(protocol: String, method: String, uri: String, headers: Map[String, String], contentType: String) {
    def httpProtocol: HttpProtocol = HttpProtocol(protocol)

    def httpMethod = method match {
      case "CONNECT" => HttpMethods.CONNECT
      case "DELETE"  => HttpMethods.DELETE
      case "GET"     => HttpMethods.GET
      case "HEAD"    => HttpMethods.HEAD
      case "OPTIONS" => HttpMethods.OPTIONS
      case "PATCH"   => HttpMethods.PATCH
      case "POST"    => HttpMethods.POST
      case "PUT"     => HttpMethods.PUT
      case "TRACE"   => HttpMethods.TRACE
      case custom    => HttpMethod.custom(custom)
    }

    def entityContentType: Either[List[ErrorInfo], ContentType] = ContentType.parse(contentType)

    def asRequest: HttpRequest = {
      val parsedHeaders = headers.map {
        case (key, value) =>
          HttpHeader.parse(key, value) match {
            case ParsingResult.Ok(h, _)     => h
            case ParsingResult.Error(error) => sys.error(s"Couldn't read back header '$key' '${value}': $error")
          }
      }

      HttpRequest(protocol = httpProtocol, method = httpMethod, uri = Uri(uri)).withHeaders(parsedHeaders.toList)
    }
  }

  case class FileRequestDao(baseDir: Path)(implicit mat: Materializer) extends RequestDao {
    type ID = Int

    import better.files._

    private val counter = new AtomicInteger(baseDir.toFile.toScala.children.size)

    override def writeDown(req: HttpRequest) = {
      save(counter.incrementAndGet(), req)
    }

    def fileName(id: Int, requestMade: ZonedDateTime = ZonedDateTime.now): String = {
      val epoch = requestMade.toEpochSecond
      s"${id}_${epoch}"
    }

    def save(id: Int, req: HttpRequest): Future[Int] = {
      import mat.executionContext
      val under: Path = baseDir.resolve(fileName(id)).toFile.toScala.createDirectories().path

      val headerPairs = req.headers.map { (header: HttpHeader) =>
        header.name -> header.value
      }
      val json = SavedRequest(req.protocol.value, req.method.value, req.uri.toString(), headerPairs.toMap, req.entity.contentType.value).asJson.noSpaces

      under.resolve("request").toFile.toScala.write(json)
      req.entity.dataBytes.runWith(FileIO.toPath(under.resolve("body"))).map { _ =>
        id
      }
    }

    def savedAt(id: Int): Option[Path] = {
      val prefix = id + "_"

      baseDir.toFile.toScala.children

      baseDir.findFirst(1)(_.fileName.startsWith(prefix))
    }

    def unmarshal(path: Path): Either[io.Serializable, HttpRequest] = {
      decode[SavedRequest](path.resolve("request").text).right.flatMap { savedRequest =>
        savedRequest.entityContentType.right.map { ct =>
          val entity = HttpEntity.fromPath(ct, path.resolve("body"))
          val req    = savedRequest.asRequest
          req.withEntity(entity)
        }
      }
    }

    override def read(id: Int): Future[HttpRequest] = {
      savedAt(id) match {
        case None => Future.failed(new Exception(s"$id not found"))
        case Some(path) =>
          unmarshal(path) match {
            case Left(err)  => Future.failed(new Exception(err.toString))
            case Right(req) => Future.successful(req)
          }
      }
    }

    override def findBetween(before: ZonedDateTime, after: ZonedDateTime): Future[List[Int]] = {
      ???
    }

    override def findByUri(text: String): Future[List[Int]] = ???

    override def findByBody(text: String): Future[List[Int]] = ???

  }

}
