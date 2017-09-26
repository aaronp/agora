package agora.exec.rest.ws

import agora.api.exchange.WorkSubscription
import agora.api.worker.HostLocation
import agora.rest.{BaseRoutesSpec}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods}
import akka.http.scaladsl.testkit.WSProbe
import io.circe.syntax._
import io.circe.generic.auto._

class WebsocketWorkerRoutesTest extends BaseRoutesSpec {
  "WebsocketWorkerRoutes.route" should {
    "handle POST /rest/websocket/create" in {

      val route = WebsocketWorkerRoutes().websocketRoute

      // tests:
      // create a testing probe representing the client-side
      val wsClient: WSProbe = WSProbe()

      // WS creates a WebSocket request for testing

      val request = WS("/rest/websocket/create", wsClient.flow)
        .withMethod(HttpMethods.POST)
        .withEntity(
          HttpEntity(ContentTypes.`application/json`, WorkSubscription(HostLocation.localhost(1234)).asJson.noSpaces))

      request ~> route ~> check {

//        wsClient.expectMessage("")
      }

    }
  }
}
