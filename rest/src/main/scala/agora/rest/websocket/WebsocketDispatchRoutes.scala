package agora.rest.websocket

import agora.api.streams.BasePublisher
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

/**
  * Adds the ability to dispatch messages coming in from a websocket
  */
class WebsocketDispatchRoutes[T](publisher: BasePublisher[T]) extends StrictLogging {

  val server = new ChatServer

  def chat: Route = {
    path("chat") {
      extractMaterializer { implicit materializer =>
        extractExecutionContext { implicit ec =>
          logger.debug("starting chat")
          handleWebSocketMessages(server.flow)
        }
      }
    }
  }
}
