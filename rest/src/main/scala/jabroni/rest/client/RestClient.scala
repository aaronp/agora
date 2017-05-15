package jabroni.rest.client

import java.io.Closeable
import java.nio.charset.StandardCharsets

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import jabroni.api.worker.HostLocation

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

trait RestClient extends Closeable {

  def send(request: HttpRequest): Future[HttpResponse]
}

object RestClient {

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): RestClient = {
    new AkkaClient(location)
  }

  class AkkaClient(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer) extends RestClient with StrictLogging {

    import mat._

    private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
      logger.info(s"Connecting to http://${location.host}:${location.port}")
      http.outgoingConnection(location.host, location.port)
    }

    def send(request: HttpRequest): Future[HttpResponse] = {
      logger.debug(s"Sending $request")
      val future = Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
      future.onComplete {
        case result => logger.debug(s"$request got $result")
      }
      future
    }


    private val http = Http()

    override def close(): Unit = {
      logger.info(s"(not) Closing client to http://${location.host}:${location.port}")
      //      http.system.terminate()
    }
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
