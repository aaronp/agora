package jabroni.rest.client

import java.io.Closeable
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import jabroni.api.worker.HostLocation

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

trait RestClient extends Closeable {
  def send(request: HttpRequest): Future[HttpResponse]
  override def close = {}
}

object RestClient {

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): RestClient = {
    new AkkaClient(location)
  }


  def decodeResponse(resp: HttpResponse) = {
    val decoder = resp.encoding match {
      case HttpEncodings.gzip => Gzip
      case HttpEncodings.deflate => Deflate
      case HttpEncodings.identity => NoCoding
    }
    decoder.decode(resp)
  }


  object implicits {
    implicit class RichHttpResponse(val resp: HttpResponse) extends AnyVal {
      def as[T: Decoder : ClassTag](implicit ec: ExecutionContext, mat: Materializer): Future[T] = {
        val bytes = resp.entity.dataBytes.runReduce(_ ++ _)
        val jsonStringFuture: Future[String] = bytes.map(_.decodeString(StandardCharsets.UTF_8))
        jsonStringFuture.map { jsonString =>
          import io.circe.parser._

          parse(jsonString) match {
            case Left(err) =>
              throw new Exception(s"Couldn't parse response (${resp.status}) '$jsonString' as json: $err", err)
            case Right(json) =>
              json.as[T] match {
                case Left(extractErr) =>
                  val className = implicitly[ClassTag[T]].runtimeClass
                  throw new Exception(s"Couldn't extract response (${resp.status}) $json as $className : $extractErr", extractErr)
                case Right(tea) => tea
              }
          }
        }
      }
    }
  }
}
