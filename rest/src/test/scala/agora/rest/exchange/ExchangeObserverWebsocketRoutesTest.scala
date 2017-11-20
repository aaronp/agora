package agora.rest.exchange

import agora.api.exchange.ServerSideExchange
import agora.api.exchange.observer.{ExchangeNotificationMessage, OnStateOfTheWorld}
import agora.rest.BaseRoutesSpec
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.testkit.WSProbe
import io.circe.parser._

class ExchangeObserverWebsocketRoutesTest extends BaseRoutesSpec {

  "ExchangeObserverWebsocketRoutes.route" should {
    "handle websockets POSTed to the observe route" in {

      object instance extends ExchangeObserverWebsocketRoutes {
        override val exchange = ServerSideExchange()
      }

      // tests:
      // create a testing probe representing the client-side
      val wsClient = WSProbe()

      // WS creates a WebSocket request for testing
      WS("/observe", wsClient.flow) ~> instance.observe ~> check {
        // check response for WS Upgrade headers
        isWebSocketUpgrade shouldEqual true

        // expect to see the state-of-the-world
        val sowEvent = wsClient.expectMessage() match {
          case TextMessage.Strict(jsonString) =>
            decode[ExchangeNotificationMessage](jsonString) match {
              case Right(sow: OnStateOfTheWorld) => sow.stateOfTheWorld
              case other                         => fail(s"didn't decode ExchangeNotificationMessage: $other")
            }
          case other => fail(s"didn't get a strict msg: $other")
        }
        sowEvent.jobs shouldBe (empty)
        sowEvent.subscriptions shouldBe (empty)
      }
    }
  }
}
