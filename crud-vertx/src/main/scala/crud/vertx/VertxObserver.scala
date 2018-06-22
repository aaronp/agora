package crud.vertx

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.scala.core.http.{WebSocket, WebSocketFrame}
import monix.reactive.Observer

class VertxObserver(socketObserver: WebSocket => Observer[WebSocketFrame]) {

  object Socket extends Handler[WebSocket] {
    override def handle(websocket: WebSocket): Unit = {
      val obs = socketObserver(websocket)
      websocket.frameHandler(Frames(obs))
      websocket.endHandler(Complete(obs))
      websocket.closeHandler(Close(obs))
    }
  }

  case class Errors(observer: Observer[WebSocketFrame]) extends Handler[Throwable] {
    override def handle(event: Throwable): Unit = {
      observer.onError(event)
    }
  }

  case class Close(observer: Observer[WebSocketFrame]) extends Handler[Unit] {
    override def handle(event: Unit): Unit = {
      observer.onError(new Exception("Socket closed"))
    }
  }

  case class Complete(observer: Observer[WebSocketFrame]) extends Handler[Unit] {
    override def handle(event: Unit): Unit = {
      observer.onComplete()
    }
  }

  case class Frames(observer: Observer[WebSocketFrame]) extends Handler[WebSocketFrame] {
    override def handle(event: WebSocketFrame): Unit = {
      observer.onNext(event)
    }
  }

}

object VertxObserver extends StrictLogging {

  def logOnError(err: Throwable) = logger.error(s"got: $err")
//
//  def apply(onFrame: WebSocketFrame => Ack, onErr: Throwable => Unit = logOnError) = {
//    new VertxObserver(new Observer.Sync[WebSocketFrame] {
//      override def onNext(elem: WebSocketFrame) = onFrame(elem)
//
//      override def onError(ex: Throwable): Unit = onErr(ex)
//
//      override def onComplete(): Unit = {
//        logger.info("Complete")
//      }
//    })
//  }
}
