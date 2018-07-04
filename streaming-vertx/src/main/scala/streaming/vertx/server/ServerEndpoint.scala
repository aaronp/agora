package streaming.vertx.server

import io.vertx.scala.core.http.ServerWebSocket
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import streaming.api.{Endpoint, WebFrame}
import streaming.vertx.WebFrameEndpoint

import scala.concurrent.duration.Duration

/**
  * A specialised endpoint which retains a reference to the socket to which is it connected,
  * which can be queried for e.g. the uri, query string, etc
  *
  * @param socket
  * @param from
  * @param to
  */
final class ServerEndpoint(val socket: ServerWebSocket, to: Observer[WebFrame], from: Observable[WebFrame])
    extends Endpoint[WebFrame, WebFrame](to, from)
    with Endpoint.Socket

object ServerEndpoint {
  def apply(socket: ServerWebSocket)(implicit timeout: Duration, scheduler: Scheduler): ServerEndpoint = {
    val (obs, frameSource) = WebFrameEndpoint.replay(socket)
    socket.accept()
    new ServerEndpoint(socket, obs, frameSource)
  }

}
