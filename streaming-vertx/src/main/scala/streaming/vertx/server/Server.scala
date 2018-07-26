package streaming.vertx.server

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServer, HttpServerRequest, ServerWebSocket}
import monix.execution.Scheduler
import monix.reactive.Observable
import streaming.api.HostPort
import streaming.rest.RestRequestContext

import scala.concurrent.duration.Duration

object Server {

  type OnConnect = ServerEndpoint => Unit

  object LoggingHandler extends Handler[HttpServerRequest] with StrictLogging {
    override def handle(event: HttpServerRequest): Unit = {
      logger.info(s"Received $event")
    }
  }

  def startSocket(port: Int, nullableName: String = null)(onConnect: OnConnect)(implicit timeout: Duration, scheduler: Scheduler): ScalaVerticle = {
    val name = Option(nullableName).getOrElse("general")

    val websocketHandler = ServerWebSocketHandler.replay(name)(onConnect)
    start(HostPort.localhost(port), LoggingHandler, websocketHandler)
  }

  def start(port: Int, requestHandler: Handler[HttpServerRequest] = LoggingHandler, nullableName: String = null)(
      onConnect: PartialFunction[String, OnConnect])(implicit timeout: Duration, scheduler: Scheduler): ScalaVerticle = {
    val name             = Option(nullableName).getOrElse("general")
    val websocketHandler = RoutingSocketHandler(onConnect.andThen(ServerWebSocketHandler.replay(name)))
    start(HostPort.localhost(port), requestHandler, websocketHandler)
  }

  def start(hostPort: HostPort, requestHandler: Handler[HttpServerRequest])(socketByRoute: PartialFunction[String, Handler[ServerWebSocket]]): ScalaVerticle = {
    start(hostPort, requestHandler, RoutingSocketHandler(socketByRoute))
  }

  def start(hostPort: HostPort, requestHandler: Handler[HttpServerRequest], socketHandler: Handler[ServerWebSocket]): ScalaVerticle = {
    object Server extends ScalaVerticle {
      vertx = Vertx.vertx()

      override def start(): Unit = {
        vertx
          .createHttpServer()
          .requestHandler(requestHandler)
          .websocketHandler(socketHandler)
          .listen(hostPort.port, hostPort.host)
      }
    }
    Server.start()
    Server
  }

  def startRest(hostPort: HostPort)(implicit scheduler: Scheduler): Observable[RestRequestContext] = {
    val restHandler = RestHandler()
    object RestVerticle extends ScalaVerticle with StrictLogging {
      vertx = Vertx.vertx()
      println(hostPort)

      val listenHandler: Handler[AsyncResult[HttpServer]] = { res =>
        if (res.succeeded()) {
          logger.info(s"Runnig on ${res.result().actualPort()}")
        } else {
          logger.error(s"Failed to start server!")

        }
      }

      override def start(): Unit = {
        vertx
          .createHttpServer()
          .requestHandler(restHandler.handle)
          .listen(hostPort.port, listenHandler)
      }
    }
    RestVerticle.start()

    restHandler.requests
  }
}
