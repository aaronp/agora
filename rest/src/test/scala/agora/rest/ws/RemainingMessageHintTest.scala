package agora.rest.ws

import agora.BaseSpec
//import io.circe.generic.auto._
import io.circe.syntax._

class RemainingMessageHintTest extends BaseSpec {
  "RemainingMessageHint.asJson" should {

    val msgs = List[RemainingMessageHint](RemainingMessageHint.single, RemainingMessageHint.unknown, RemainingMessageHint(2))
    msgs.foreach { expected =>
      s"marshal $expected to and from json" in {
        val json = expected.asJson
        println(json)
        json.as[RemainingMessageHint] shouldBe Right(expected)
      }
    }
  }
}
