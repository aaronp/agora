package streaming.vertx.server

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServerRequest, ServerWebSocket}
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.sockjs.{SockJSHandler, SockJSHandlerOptions, SockJSSocket}
import monix.execution.Scheduler
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

  def start(hostPort: HostPort, requestHandler: Handler[HttpServerRequest], socketHandler: Handler[ServerWebSocket]): ScalaVerticle = {

    object Server extends ScalaVerticle {
      vertx = Vertx.vertx()

//      val router = Router.router(vertx)
//
//      def socketJS = {
//
//        // https://github.com/vert-x3/vertx-web/blob/master/vertx-web/src/main/java/examples/WebExamples.java
//        val opts = SockJSHandlerOptions().setHeartbeatInterval(2000)
//        object SH extends Handler[SockJSSocket] {
//          override def handle(event: SockJSSocket): Unit = {
//
//          }
//        }
//        val s: SockJSHandler = SockJSHandler.create(vertx, opts).socketHandler(SH)
//        router.route("/foo").handler(s)
//      }


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
