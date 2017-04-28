package jabroni.rest.client

import java.io.Closeable
import java.nio.charset.StandardCharsets

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder

import scala.concurrent.{ExecutionContext, Future}

trait RestClient extends Closeable {

  def send(request: HttpRequest): Future[HttpResponse]
}

object RestClient {

  def apply(conf: ClientConfig): RestClient = new AkkaClient(conf)

  class AkkaClient(config: ClientConfig) extends RestClient with StrictLogging {

    import config.implicits._

    private lazy val remoteServiceConnectionFlow: Flow[HttpRequest, HttpResponse, Any] = {
      logger.info(s"Connecting to http://${config.host}:${config.port}")
      http.outgoingConnection(config.host, config.port)
    }

    def send(request: HttpRequest): Future[HttpResponse] = {
      logger.info(s"Sending $request")
      Source.single(request).via(remoteServiceConnectionFlow).runWith(Sink.head)
      //      http.singleRequest(request)
    }


    private val http = {
      Http()
    }

    override def close(): Unit = {
      logger.info(s"Closing client to http://${config.host}:${config.port}")
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
