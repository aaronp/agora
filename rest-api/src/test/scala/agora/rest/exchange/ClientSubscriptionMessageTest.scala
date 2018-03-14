package agora.rest.exchange

import agora.BaseRestApiSpec
import io.circe.syntax._

class ClientSubscriptionMessageTest extends BaseRestApiSpec {
  "ClientSubscriptionMessage.unapply" should {
    "not deserialize invalid json" in {
      ClientSubscriptionMessage.unapply("invalid") shouldBe None
    }

    List[ClientSubscriptionMessage](
      ClientSubscriptionMessage.takeNext(456),
      ClientSubscriptionMessage.cancel
    ).foreach { expected =>
      s"serialise ${expected.getClass.getSimpleName} to/from json" in {
        val json = expected.asJson
        ClientSubscriptionMessage.unapply(json.noSpaces) shouldBe Some(expected)
      }
    }
  }
}
