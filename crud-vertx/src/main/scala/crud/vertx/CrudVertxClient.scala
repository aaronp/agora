package crud.vertx

import crud.api.ResolvedEndpoint
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, WebSocket, WebSocketFrame}
import monix.reactive.Observer

class CrudVertxClient private(endpoint: ResolvedEndpoint, client: VertxObserver, impl: Vertx = Vertx.vertx()) extends ScalaVerticle {
  vertx = impl

  val httpsClient: HttpClient = vertx.createHttpClient.websocket(endpoint.port, host = endpoint.host, endpoint.uri, client.Socket)

  start()
}

object CrudVertxClient {
  def start(endpoint: ResolvedEndpoint)(client: WebSocket => Observer[WebSocketFrame]): CrudVertxClient = {
    new CrudVertxClient(endpoint, new VertxObserver(client))

  }
}
