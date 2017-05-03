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

trait RestClient extends Closeable {

  def send(request: HttpRequest): Future[HttpResponse]
}

object RestClient {

  def apply(location : HostLocation)(implicit sys : ActorSystem, mat : Materializer) : RestClient = {
    new AkkaClient(location)
  }

  def apply(conf: ClientConfig): RestClient = {
    import conf.implicits._
    apply(conf.location)
  }

  class AkkaClient(location : HostLocation)(implicit sys : ActorSystem, mat : Materializer) extends RestClient with StrictLogging {
    private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
      logger.info(s"Connecting to http://${location.host}:${location.port}")
      http.outgoingConnection(location.host, location.port)
    }

    def send(request: HttpRequest): Future[HttpResponse] = {
      logger.info(s"Sending $request")
      Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
      //      http.singleRequest(request)
    }


    private val http = Http()

    override def close(): Unit = {
      logger.info(s"Closing client to http://${location.host}:${location.port}")
      //      http.system.terminate()
    }
  }

  object implicits {

    implicit class RichHttpResponse(val resp: HttpResponse) extends AnyVal {
      def as[T: Decoder](implicit ec: ExecutionContext, mat: Materializer): Future[T] = {
        val bytes = resp.entity.dataBytes.runReduce(_ ++ _)
        val jsonStringFuture: Future[String] = bytes.map(_.decodeString(StandardCharsets.UTF_8))
        //          jsonStringFuture
        jsonStringFuture.map { json =>
          import io.circe.parser._
          parse(json).right.get.as[T].right.get
        }
      }
    }
  }
}
