package agora.rest.stream

import agora.rest.BaseRoutesSpec
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.testkit.WSProbe
import io.circe.parser.decode

class StreamRoutesTest extends BaseRoutesSpec {
  "StreamRoutes.routes" should {
    "sent updates based on json flowing into it" in {

      // we have one route for pushing data in, and another for reading data from it.

      val streamRoutes = new StreamRoutes

      // tests:
      // create a testing probe representing the client-side
      val wsClient: WSProbe = WSProbe()

      val routes = streamRoutes.routes

      // WS creates a WebSocket publisher 'dave' w/ subscription queue size of 10
      WS("/rest/stream/publish/dave?maxCapacity=10", wsClient.flow) ~> routes ~> check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true

        // sent an explicit request for taking 3 from the new publisher
        Get("/rest/stream/publish/dave/request/3") ~> routes

        // verify we receive the TakeNext
        wsClient.expectMessage() match {
          case TextMessage.Strict(jsonString) =>
            decode[agora.rest.exchange.ClientSubscriptionMessage](jsonString) match {
              case Right(takeNext) => takeNext shouldBe TakeNext(3)
              case other           => fail(s"didn't decode ExchangeNotificationMessage: $other")
            }
          case other => fail(s"didn't get a strict msg: $other")
        }
      }
    }
  }
}
