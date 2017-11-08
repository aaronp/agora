package agora.rest.ws

import agora.BaseSpec
import io.circe.generic.auto._

class WebSocketEnvelopeTest extends BaseSpec {

  import WebSocketEnvelopeTest._

  "WebSocketEnvelope.unapply" should {
    "be able to deserialize types for which there is a decoder" in {
      val textMsg                                     = WebSocketEnvelope.textMessage(Meh("hello", 123))
      val envelopeOpt: Option[WebSocketEnvelope[Meh]] = WebSocketEnvelope.FromJson.unapply[Meh](textMsg.text)
      envelopeOpt.map(_.value) shouldBe Some(Meh("hello", 123))
    }
  }

}

object WebSocketEnvelopeTest {

  case class Meh(id: String, x: Int)

}
