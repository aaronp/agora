package crud.vertx

import io.vertx.scala.core.http.{WebSocket, WebSocketFrame}
import monix.reactive.Observer

case class WebSocketObserver(websocket: WebSocket, observer :Observer[WebSocketFrame]) {


}
