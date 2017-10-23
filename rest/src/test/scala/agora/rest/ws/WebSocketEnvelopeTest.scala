package agora.rest.ws

import agora.BaseSpec
import io.circe.generic.auto._
import io.circe.syntax._

class WebSocketEnvelopeTest extends BaseSpec {

  import WebSocketEnvelopeTest._

  "WebSocketEnvelope.unapply" should {
    "be able to deserialize types for which there is a decoder" in {
      val expected = WebSocketEnvelope.textMessage(Meh("hello", 123))
//      val backAgain @ WebSocketEnvelope(_, _, _) = expected.text
//      backAgain shouldBe expected
    }
  }

}

object WebSocketEnvelopeTest {

  case class Meh(id: String, x: Int)

}
