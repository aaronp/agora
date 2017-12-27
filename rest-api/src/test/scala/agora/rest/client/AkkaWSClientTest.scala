package agora.rest.client

import agora.BaseSpec
import agora.api.streams.BaseSubscriber
import akka.http.scaladsl.model.StatusCodes

class AkkaWSClientTest extends BaseSpec {

  "AkkaWSClient" should {
    "work" in {

      val jsonObs = BaseSubscriber.fromJson() {
        case (sub, json) =>
          println(json)
          sub.request(1)
      }

      val (resp, client) = AkkaWSClient("ws://echo.websocket.org", jsonObs).futureValue
      resp.status shouldBe StatusCodes.UpgradeRequired
      println(client)

    }
  }

}
