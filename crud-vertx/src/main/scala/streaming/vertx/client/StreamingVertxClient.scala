package streaming.vertx.client

import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, WebSocket, WebSocketFrame}
import monix.reactive.Observer
import streaming.api.EndpointCoords

class StreamingVertxClient private(endpoint: EndpointCoords, client: VertxObserver, impl: Vertx = Vertx.vertx()) extends ScalaVerticle {
  vertx = impl

  val httpsClient: HttpClient = vertx.createHttpClient.websocket(endpoint.port, host = endpoint.host, endpoint.uri, client.Socket)

  start()
}

object StreamingVertxClient {
  def start(endpoint: EndpointCoords)(client: WebSocket => Observer[WebSocketFrame]): StreamingVertxClient = {
    new StreamingVertxClient(endpoint, new VertxObserver(client))

  }
}
