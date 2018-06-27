package streaming.vertx.server

import io.vertx.scala.core.http.ServerWebSocket
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import streaming.api.{Endpoint, WebFrame}
import streaming.vertx.WebFrameEndpoint

import scala.concurrent.duration.Duration

final class VertxWebSocketEndpoint(val socket: ServerWebSocket, from: Observable[WebFrame], to: Observer[WebFrame]) extends Endpoint[WebFrame, WebFrame](from, to)

object VertxWebSocketEndpoint {
  def apply(socket: ServerWebSocket)(implicit timeout: Duration, scheduler: Scheduler): VertxWebSocketEndpoint = {
    val (frameSource, obs) = WebFrameEndpoint(socket)
//
//    val fromClient: Pipe[WebFrame, WebFrame] = Pipe.replay[WebFrame]
//    val (frameSink, frameSource: Observable[WebFrame]) = fromClient.concurrent
//
//    val completed = new AtomicBoolean(false)
//
//    def markComplete() = {
//      if (completed.compareAndSet(false, true)) {
//        frameSink.onComplete()
//      }
//    }
//
//    socket.frameHandler(new Handler[WebSocketFrame] {
//      override def handle(event: WebSocketFrame): Unit = {
//        if (event.isClose()) {
//          markComplete()
//        } else {
//          val fut = frameSink.onNext(WebSocketFrameAsWebFrame(event))
//          // TODO - we should apply back-pressure, but also not block the event loop.
//          // need to apply some thought here if this can work in the general case,
//          // of if this should be made more explicit
//          //Await.result(fut, timeout)
//        }
//      }
//    })
//
    socket.accept()
//    socket.exceptionHandler(new Handler[Throwable] {
//      override def handle(event: Throwable): Unit = {
//        frameSink.onError(event)
//        socket.close()
//      }
//    })
//    socket.endHandler(new Handler[Unit] {
//      override def handle(event: Unit): Unit = markComplete()
//    })
    new VertxWebSocketEndpoint(socket, frameSource, obs)
  }
}
