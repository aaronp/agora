package jabroni.rest.client

import java.io.Closeable

import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}
import io.circe.Decoder
import jabroni.api.worker.HostLocation

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

trait RestClient extends Closeable {
  def send(request: HttpRequest): Future[HttpResponse]

  /** You could argue that putting this here sullies the otherwise clean
    * functional interface, but that sacrifice is laid upon the alter of sanity.
    * It's much easier to reason about which materializer/execution context is used
    * when it comes baked in with a client -- in particular in the retry/failover scenarios.
    *
    * We don't want to e.g. accidentally bring another materializer (and by extension its
    * execution context) in scope from a stopped actor system
    *
    * @return
    */
  implicit def materializer: Materializer

  implicit def executionContext: ExecutionContext = materializer.executionContext

  override def close = {}
}

object RestClient {

  def apply(location: HostLocation, mkSystem: () => ActorMaterializer): RestClient = {
    // we do this here to be sure the akka client owns the produced actor system and can thus also
    // shut it down
    val am = mkSystem()
    new AkkaClient(location, am.system, am)
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

    type HandlerError = (Option[String], HttpResponse, Exception)

    implicit class RichHttpResponse(val resp: HttpResponse) extends AnyVal {
      def justThrow[T]: HandlerError => Future[T] = (e: HandlerError) => throw e._3

      def as[T: Decoder : ClassTag](onErr: HandlerError => Future[T] = justThrow[T])(implicit ec: ExecutionContext, mat: Materializer): Future[T] = {

        def decode(jsonString: String) = {
          import io.circe.parser._
          parse(jsonString) match {
            case Left(err) => onErr(Option(jsonString), resp, err)

            case Right(json) =>
              json.as[T] match {
                case Left(extractErr) =>
                  val className = implicitly[ClassTag[T]].runtimeClass
                  onErr(Option(jsonString), resp, new Exception(s"Couldn't extract response (${resp.status}) $json as $className : $extractErr", extractErr))
                case Right(tea) => Future.successful(tea)
              }
          }
        }

        resp.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String).flatMap(decode)
      }
    }
  }
}
