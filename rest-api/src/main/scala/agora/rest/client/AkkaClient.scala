package agora.rest.client

import agora.api.worker.HostLocation
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging

import scala.compat.Platform
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * A [[RestClient]] based on akka [[Http]]
  * @param location
  * @param system
  * @param materializer
  */
class AkkaClient(val location: HostLocation, system: ActorSystem, override implicit val materializer: Materializer) extends RestClient with StrictLogging {

  private val hostPort = location.asURL

  override def toString = s"AkkaClient($hostPort)"

  private def onError(err: Throwable): Throwable = {
    logger.error(s"connecting to $hostPort threw $err")
    err
  }

  private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
    logger.info(s"Connecting to $hostPort")
    val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = http.outgoingConnection(location.host, location.port) //.map(decodeResponse)
    flow.mapError {
      case err => onError(err)
    }
  }

  def send(request: HttpRequest): Future[HttpResponse] = {
    logger.trace(s"Sending $hostPort ==> ${request.method} ${request.uri}")
    val started = Platform.currentTime
    val future = try {
      Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
    } catch {
      case NonFatal(e) =>
        Future.failed(e)
    }

    def took = s"${Platform.currentTime - started}ms"

    future.onComplete {
      case Success(resp) if resp.status.intValue() == 200 => logger.debug(s"$hostPort/${request.uri} took ${took}")
      case Success(resp)                                  => logger.debug(s"$hostPort/${request.uri} took ${took} (status ${resp.status})")
      case Failure(err)                                   => logger.error(s"$hostPort/${request.uri} took ${took} and threw ${err}")
    }
    future
  }

  private val http = Http()(system)

  override def close(): Unit = {
    logger.info(s"Closing client to http://${location.host}:${location.port}")
    system.terminate()
  }
}
