package agora.rest.client

import java.io.Closeable

import akka.http.scaladsl.coding.{Deflate, Gzip, NoCoding}
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}
import io.circe.Decoder
import agora.api.worker.HostLocation

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  * Represents the client to a web service,
  */
trait RestClient extends Closeable {

  /**
    * Does what it says on the tin -- send the [[HttpRequest]] and eventually return a [[HttpResponse]].
    *
    * The client is presumably established for a know [[HostLocation]], but
    * @param request the request to send
    * @return the response in a future
    */
  def send(request: HttpRequest): Future[HttpResponse]

  /** You could argue that putting this here sullies the otherwise clean
    * functional interface, but that sacrifice is laid upon the alter of sanity.
    * It's much easier to reason about which materializer/execution context is used
    * when it comes baked in with a client -- in particular in the retry/failover scenarios.
    *
    * We don't want to e.g. accidentally bring another materializer (and by extension its
    * execution context) in scope from a stopped actor system
    *
    * @return the materializer associated w/ this client
    */
  implicit def materializer: Materializer

  /**
    * See materializer comment
    *
    * @return the execution context associated w/ this client
    */
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
      case HttpEncodings.gzip     => Gzip
      case HttpEncodings.deflate  => Deflate
      case HttpEncodings.identity => NoCoding
    }
    decoder.decodeMessage(resp)
  }

  object implicits {

    type HandlerError = (Option[String], HttpResponse, Exception)

    implicit class RichHttpResponse(val resp: HttpResponse) extends AnyVal {
      def justThrow[T]: HandlerError => Future[T] = (e: HandlerError) => throw e._3

      def as[T: Decoder: ClassTag](onErr: HandlerError => Future[_ <: T] = justThrow[T])(implicit ec: ExecutionContext, mat: Materializer): Future[_ <: T] = {

        def decode(jsonString: String) = {
          import io.circe.parser._
          parse(jsonString) match {
            case Left(err) => onErr((Option(jsonString), resp, err))

            case Right(json) =>
              json.as[T] match {
                case Left(extractErr) =>
                  val className = implicitly[ClassTag[T]].runtimeClass
                  val exp       = new Exception(s"Couldn't extract response (${resp.status}) $json as $className : $extractErr", extractErr)
                  onErr((Option(jsonString), resp, exp))
                case Right(tea) => Future.successful(tea)
              }
          }
        }

        resp.entity.dataBytes.runReduce(_ ++ _).map(_.utf8String).flatMap(decode)
      }
    }

  }

}
