package jabroni.rest.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.worker.HostLocation

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.util.control.NonFatal


class AkkaClient(val location: HostLocation)(implicit sys: ActorSystem, mat: Materializer) extends RestClient with StrictLogging {

  import mat._

  private val hostPort = s"http://${location.host}:${location.port}"

  private def onError(err: Throwable): Throwable = {
    logger.info(s"connecting to $hostPort threw $err")
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
    logger.debug(s"Sending $hostPort ==> ${pprint.stringify(request)}")
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
  }
}