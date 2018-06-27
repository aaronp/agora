package streaming.vertx.server

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.scala.core.http.{ServerWebSocket, WebSocketFrame}
import monix.reactive.Observer


case class SocketHandler(newObserver: ServerWebSocket => Observer[WebSocketFrame]) extends Handler[ServerWebSocket] with StrictLogging {
  override def handle(websocket: ServerWebSocket): Unit = {
    logger.info(s"Handling request on ${websocket.uri()}")
    val observer: Observer[WebSocketFrame] = newObserver(websocket)
    SocketHandler.connect(websocket, observer)
  }
}

object SocketHandler {

  def connect(websocket: ServerWebSocket, observer: Observer[WebSocketFrame]): ServerWebSocket = {
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