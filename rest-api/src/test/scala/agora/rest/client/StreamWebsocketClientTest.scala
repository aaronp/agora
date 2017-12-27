package agora.rest.client

import agora.BaseSpec
import agora.api.streams.BaseSubscriber
import agora.rest.HasMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import io.circe.Json

class StreamWebsocketClientTest extends BaseSpec with HasMaterializer {

  "StreamWebsocketClient" should {
    "work" in {
      implicit val http = Http()

      val jsonObs = BaseSubscriber.fromJson[Json](1) {
        case (sub, json) =>
          println(json)
          sub.request(1)
      }

      val (resp, client) = StreamWebsocketClient("ws://echo.websocket.org", jsonObs).futureValue
      resp.status shouldBe StatusCodes.SwitchingProtocols
      println(client)
      client.takeNext(3)
      client.takeNext(1)
      client.takeNext(1)

    }
  }

}
