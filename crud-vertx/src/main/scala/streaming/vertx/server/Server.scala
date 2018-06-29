package streaming.vertx.server

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServerRequest, ServerWebSocket, WebSocketFrame}
import monix.execution.Scheduler
import monix.reactive.Observer
import streaming.api.HostPort

import scala.concurrent.duration.Duration

object Server {

  object LoggingHandler extends Handler[HttpServerRequest] with StrictLogging {
    override def handle(event: HttpServerRequest): Unit = {
      logger.info(s"Received $event")
    }
  }

  def start(port: Int)(onConnect: ServerEndpoint => Unit)(implicit timeout: Duration, scheduler: Scheduler): ScalaVerticle = {
    val websocketHandler = new ServerWebSocketHandler(onConnect)
    start(HostPort.localhost(port), LoggingHandler, websocketHandler)
  }

  def start(hostPort: HostPort,
            requestHandler: Handler[HttpServerRequest],
            socketHandler: Handler[ServerWebSocket]): ScalaVerticle = {

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
}
