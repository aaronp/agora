package streaming.vertx.client

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, HttpClientRequest, HttpClientResponse}
import monix.execution.Scheduler
import monix.reactive.subjects.Var
import monix.reactive.{Observable, Pipe}
import streaming.api.HostPort
import streaming.rest.{HttpMethod, RestInput, RestResponse}


object RestClient {
  def connect(location: HostPort)(implicit scheduler: Scheduler): RestClient = RestClient(location)
}


case class RestClient(val location: HostPort, impl: Vertx = Vertx.vertx())(implicit scheduler: Scheduler) extends ScalaVerticle with StrictLogging {
  vertx = impl
  val httpClient: HttpClient = vertx.createHttpClient

  val sendPipe: Pipe[RestInput, RestResponse] = Pipe.publishToOne[RestInput].transform { restInputs: Observable[RestInput] =>
    restInputs.flatMap(send).doOnTerminate { errOpt =>
      logger.error(s"stopping client connected to $location ${errOpt.fold("")("on error " + _)} ")
      stop()
    }
  }

  start()

  /**
    * Sends the given request, but WITHOUGH ending it
    *
    * @param req
    * @return an unended request
    */
  def send(req: RestInput): Observable[RestResponse] = {
    logger.debug(s"Sending $req to $location")

    req.uri.resolve(req.headers) match {
      case Left(bad) =>
        Observable.raiseError(new IllegalArgumentException(s"Request didn't supply required path parts: $bad"))
      case Right(parts) =>

        val uri = parts.mkString("/")

        val httpRequest: HttpClientRequest = req.uri.method match {
          case HttpMethod.GET => httpClient.get(location.port, location.host, uri)
          case HttpMethod.POST => httpClient.post(location.port, location.host, uri)
          case HttpMethod.PUT => httpClient.put(location.port, location.host, uri)
          case HttpMethod.DELETE => httpClient.delete(location.port, location.host, uri)
          case HttpMethod.HEAD => httpClient.head(location.port, location.host, uri)
          case HttpMethod.OPTIONS => httpClient.options(location.port, location.host, uri)
          case _ => null
        }

        val responseVar = Var[RestResponse](null)

        httpRequest.handler { event: HttpClientResponse =>
          logger.debug(s"Handling response from $uri : ${event.request().uri()}")
          event.bodyHandler { body =>
            val headers: Map[String, String] = {
              val headersMultiMap = event.headers()
              headersMultiMap.names.map { name =>
                name -> headersMultiMap.getAll(name).mkString(",")
              }.toMap
            }

            logger.debug(s"Setting response var from $uri ")
            responseVar := RestResponse(body.getBytes, headers, event.statusCode())
          }
        }

        if (httpRequest == null) {
          Observable.raiseError(new UnsupportedOperationException(s"Unsupported method ${req.uri.method}"))
        } else {
          val builtRequest = req.headers.foldLeft(httpRequest.setChunked(true).write(Buffer.buffer(req.bodyAsBytes))) {
            case (r, (key, value)) => r.putHeader(key, value)
          }

          builtRequest.end()

          responseVar.filter( _ != null).take(1)
        }
    }
  }
}