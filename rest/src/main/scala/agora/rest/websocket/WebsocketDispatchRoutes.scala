package agora.rest.websocket

import javax.ws.rs.Path

import agora.api.exchange.observer.ExchangeNotificationMessage
import agora.api.streams.BasePublisher
import agora.rest.exchange.ExchangeObserverWebsocketRoutes
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.swagger.annotations.{ApiOperation, ApiResponse, ApiResponses}

/**
  * Adds the ability to dispatch messages coming in from a websocket
  */
class WebsocketDispatchRoutes[T](publisher: BasePublisher[T]) extends StrictLogging {

  @Path("/rest/exchange/observe")
  @ApiOperation(
    value = "connects a websocket to the exchange to observer exchange events",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 101, message = "ExchangeNotificationMessage messages", response = classOf[ExchangeNotificationMessage])
    ))
  def observe: Route = {
    path("observe") {
      extractMaterializer { implicit materializer =>
        extractExecutionContext { implicit ec =>
          logger.debug("Adding a websocket observer")

//          val flow: Flow[Message, Message, NotUsed] = MessageFlow()
//          handleWebSocketMessages(flow)
          ???
        }
      }
    }
  }
}
