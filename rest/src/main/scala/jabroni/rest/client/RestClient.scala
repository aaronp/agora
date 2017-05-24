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

  def location: HostLocation

  def send(request: HttpRequest): Future[HttpResponse]
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

  class AkkaClient(override val location: HostLocation)(implicit sys: ActorSystem, mat: Materializer) extends RestClient with StrictLogging {

    import mat._

    private val hostPort = s"http://${location.host}:${location.port}"

    private def onConnection(future: Future[Http.OutgoingConnection]): Future[Http.OutgoingConnection] = {
      future.onComplete {
        case Success(connection) =>
          logger.debug(s"$hostPort established a connection to ${connection.localAddress} (${connection.remoteAddress})")
        case Failure(err) =>
          logger.error(s"Error establishing a connection to $hostPort : $err")
      }
      future
    }

    private def onError(err: Throwable): Throwable = {
      logger.info(s"connecting to $hostPort threw $err")
      err
    }

    private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
      logger.info(s"Connecting to $hostPort")
      val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = http.outgoingConnection(location.host, location.port) //.map(decodeResponse)
      flow.mapMaterializedValue(onConnection).mapError {
        case err => onError(err)
      }
    }

    def send(request: HttpRequest): Future[HttpResponse] = {
      logger.debug(s"Sending $hostPort ==> $request")
      val future = try {
        Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
      } catch {
        case NonFatal(e) =>
          Future.failed(e)
      }
      future.onComplete {
        case Success(resp) => logger.debug(s"$hostPort w/ $request returned w/ status ${resp.status}")
        case Failure(err) => logger.error(s"$hostPort w/ $request threw ${err}")
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
