package agora.rest.client

import agora.api.worker.HostLocation
import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * A [[RestClient]] based on akka [[Http]]
  *
  * @param location
  * @param system
  * @param materializer
  */
class AkkaClient(val location: HostLocation, system: ActorSystem, override implicit val materializer: Materializer) extends RestClient with StrictLogging {

  private val hostPort = location.asURL

  override def toString = s"AkkaClient($hostPort)"

  private def onError(err: Throwable): Throwable = {
    logger.error(s"Connecting to $hostPort threw $err")
    err
  }

  private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
    logger.info(s"Connecting to $hostPort")
    val flow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      http.outgoingConnection(location.host, location.port) //.map(decodeResponse)
    flow.mapError {
      case err => onError(err)
    }
  }

  def send(request: HttpRequest): Future[HttpResponse] = {
    logger.warn(s"Sending ${request.method.name} ==> $hostPort${request.uri}")
    try {
      Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
    } catch {
      case NonFatal(e) =>
        Future.failed(e)
    }
  }

  private val http = Http()(system)

  override def close(): Unit = stop()

  override def stop(): Future[Terminated] = {
    logger.info(s"Closing client to http://${location.host}:${location.port}")
    system.terminate()
  }
}
