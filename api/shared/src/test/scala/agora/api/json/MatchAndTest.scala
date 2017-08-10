package agora.api.json

import io.circe.Decoder.Result
import org.scalatest.{Matchers, WordSpec}

class MatchAndTest extends WordSpec with Matchers {
  "MatchAnd.Format" should {
    "encode and decode json" in {
      val and                         = MatchAnd(JPath("foo"), JPath("bar"))
      val json                        = MatchAnd.Format(and)
      val backAgain: Result[MatchAnd] = MatchAnd.Format.decodeJson(json)
      backAgain shouldBe Right(and)
    }
  }
}
