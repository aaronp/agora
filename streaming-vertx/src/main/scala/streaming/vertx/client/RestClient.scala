package streaming.vertx.client

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


object RestClient {
  def connect(coords: EndpointCoords)(implicit scheduler: Scheduler): RestClient = {
    RestClient(coords)
  }
}


case class RestClient(val coords: EndpointCoords, impl: Vertx = Vertx.vertx()) extends ScalaVerticle with StrictLogging {
  vertx = impl
  val httpClient: HttpClient = vertx.createHttpClient
  //val sendingFuture: CancelableFuture[Unit] = observable.foreach(r => send(r))

  val sendPipe: Pipe[RestInput, HttpClientResponse] = Pipe.publishToOne[RestInput].transform { restInputs =>
    restInputs.flatMap { input =>

      send(input) match {
        case Left(err) => Observable.raiseError(new Exception(err))
        case Right(req) =>
          val response: Task[MVar[HttpClientResponse]] = MVar.empty[HttpClientResponse]
          req.handler(new Handler[HttpClientResponse] {
            override def handle(event: HttpClientResponse): Unit = {
              println(s"got response $event")
              response.foreach { mvar =>
                println(s"mvar puttint $event")
                mvar.put(event)
              }
            }
          })

          req.end()
          Observable.fromTask(response.flatMap(_.read))
      }
    }.doOnTerminate { errOpt =>
      logger.error(s"stoppoing client connected to $coords ${errOpt.fold("")("on error " + _)} ")
      stop()
    }
  }

  start()

  /**
    * Sends the given request, but WITHOUGH ending it
    * @param req
    * @tparam A
    * @return an unended request
    */
  def send[A](req: RestInput): Either[String, HttpClientRequest] = {
    logger.debug(s"Sending $req")
    req.uri.resolve(req.headers).right.flatMap { parts =>
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

      if (httpRequest == null) {
        Left(s"Unsupported method ${req.uri.method}")
      } else {
        Right {
          val withHeaders = req.headers.foldLeft(httpRequest.write(Buffer.buffer(req.bodyAsBytes))) {
            case (r, (key, value)) => r.putHeader(key, value)
          }
          withHeaders
        }
      }
    }
  }
}