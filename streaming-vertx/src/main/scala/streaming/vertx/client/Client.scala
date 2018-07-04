package streaming.vertx.client

import io.vertx.core.Handler
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, WebSocket}
import monix.execution.Scheduler
import streaming.api.sockets.WebFrame
import streaming.api.{Endpoint, EndpointCoords}
import streaming.vertx.WebFrameEndpoint

import scala.concurrent.duration.Duration

class Client private (endpoint: EndpointCoords, client: Handler[WebSocket], impl: Vertx = Vertx.vertx()) extends ScalaVerticle {
  vertx = impl

  val uri : String = ??? //endpoint.uri
  val httpsClient: HttpClient = vertx.createHttpClient.websocket(endpoint.port, host = endpoint.host, uri, client)

  start()
}

object Client {
  def connect(endpoint: EndpointCoords)(onConnect: Endpoint[WebFrame, WebFrame] => Unit)(implicit timeout: Duration, scheduler: Scheduler): Client = {

    val handler = new Handler[WebSocket] {
      override def handle(event: WebSocket): Unit = {
        val (fromRemote, toRemote) = WebFrameEndpoint.replay(event)
        onConnect(Endpoint(fromRemote, toRemote))
      }
    }

    new Client(endpoint, handler)

  }
}
