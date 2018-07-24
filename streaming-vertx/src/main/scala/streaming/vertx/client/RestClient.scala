package streaming.vertx.client

import agora.io.FromBytes
import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, HttpClientRequest, HttpClientResponse}
import monix.eval.{MVar, Task}
import monix.execution.Scheduler
import monix.reactive.{Observable, Pipe}
import streaming.rest.{EndpointCoords, HttpMethod}

import scala.util.{Failure, Success, Try}


object RestClient {
  def connect(coords: EndpointCoords)(implicit scheduler: Scheduler): RestClient = {
    RestClient(coords)
  }
}


case class RestClient(val coords: EndpointCoords, impl: Vertx = Vertx.vertx())(implicit scheduler: Scheduler) extends ScalaVerticle with StrictLogging {
  vertx = impl
  val httpClient: HttpClient = vertx.createHttpClient

  val sendPipe: Pipe[RestInput, HttpClientResponse] = Pipe.publishToOne[RestInput].transform { restInputs: Observable[RestInput] =>
    restInputs.flatMap(send).doOnTerminate { errOpt =>
      logger.error(s"stopping client connected to $coords ${errOpt.fold("")("on error " + _)} ")
      stop()
    }
  }

  def restPipe[A: FromBytes]: Pipe[RestInput, Observable[Try[A]]] = sendPipe.transform { httpResponses =>

    httpResponses.map { resp =>
      val replyValue = MVar.empty[Try[A]]
      resp.bodyHandler { buffer =>
        val a: Try[A] = FromBytes[A].read(buffer.getBytes)
        replyValue.flatMap { mvar =>
          mvar.put(a)
        }
      }

      Observable.fromTask(replyValue.flatMap(_.read))
    }
  }

  start()

  /**
    * Sends the given request, but WITHOUGH ending it
    *
    * @param req
    * @tparam A
    * @return an unended request
    */
  def send(req: RestInput): Observable[HttpClientResponse] = {
    logger.debug(s"Sending $req")

    req.uri.resolve(req.headers) match {
      case Left(bad) =>
        Observable.raiseError(new IllegalArgumentException(s"Request didn't supply required path parts: $bad"))
      case Right(parts) =>

        val uri = parts.mkString("/")

        val httpRequest: HttpClientRequest = req.uri.method match {
          case HttpMethod.GET => httpClient.get(coords.port, coords.host, uri)
          case HttpMethod.POST => httpClient.post(coords.port, coords.host, uri)
          case HttpMethod.PUT => httpClient.put(coords.port, coords.host, uri)
          case HttpMethod.DELETE => httpClient.delete(coords.port, coords.host, uri)
          case HttpMethod.HEAD => httpClient.head(coords.port, coords.host, uri)
          case HttpMethod.OPTIONS => httpClient.options(coords.port, coords.host, uri)
          case _ => null
        }


        val response: Task[MVar[HttpClientResponse]] = MVar.empty[HttpClientResponse]
        httpRequest.handler(new Handler[HttpClientResponse] {
          override def handle(event: HttpClientResponse): Unit = {
            println(s"got response $event")
            response.foreach { mvar =>
              println(s"mvar putting value within task")
              mvar.put(event)
            }
          }
        })

        if (httpRequest == null) {
          Observable.raiseError(new UnsupportedOperationException(s"Unsupported method ${req.uri.method}"))
        } else {


          req.headers.foldLeft(httpRequest.setChunked(true).write(Buffer.buffer(req.bodyAsBytes))) {
            case (r, (key, value)) => r.putHeader(key, value)
          }.end()

          Observable.fromTask(response.flatMap(_.read))
        }
    }
  }
}