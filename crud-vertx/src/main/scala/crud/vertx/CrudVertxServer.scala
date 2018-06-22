package crud.vertx

import com.typesafe.scalalogging.StrictLogging
import crud.api.HostPort
import io.vertx.core.Handler
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{ServerWebSocket, WebSocketFrame}
import monix.reactive.Observer

object CrudVertxServer {

  class SocketHandler(newObserver: ServerWebSocket => Observer[WebSocketFrame]) extends Handler[ServerWebSocket] with StrictLogging {
    override def handle(websocket: ServerWebSocket): Unit = {

      logger.info(s"Handling request on ${websocket.uri()}")

      val observer = newObserver(websocket)
      websocket.accept()

      websocket.exceptionHandler(new Handler[Throwable] {
        override def handle(event: Throwable): Unit = {
          observer.onError(event)
          websocket.close()
        }
      })
      websocket.endHandler(new Handler[Unit] {
        override def handle(event: Unit): Unit = observer.onComplete()
      })
      websocket.frameHandler(new Handler[WebSocketFrame] {
        override def handle(event: WebSocketFrame): Unit = {
          observer.onNext(event)
        }
      })
    }
  }

  def start(port: Int)(frameHandler: ServerWebSocket => Observer[WebSocketFrame]): ScalaVerticle = {
    start(HostPort.localhost(port), new SocketHandler(frameHandler))
  }

  def start(hostPort: HostPort, socketHandler: Handler[ServerWebSocket]): ScalaVerticle = {
    //    val options = HttpServerOptions().setHost(hostPort.host).setPort(hostPort.port)

    object Server extends ScalaVerticle {
      vertx = Vertx.vertx()
      override def start(): Unit = {
        vertx
          .createHttpServer()
          //          .requestHandler(handler)
          .websocketHandler(socketHandler)
          .listen(hostPort.port, hostPort.host)
      }
    }
    Server.start()
    Server

  }

}
