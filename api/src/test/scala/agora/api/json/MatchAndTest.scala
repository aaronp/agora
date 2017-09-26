package agora.api.json

import agora.BaseSpec
import io.circe.Decoder.Result
import agora.api.Implicits._

class MatchAndTest extends BaseSpec {
  "MatchAll and foo" should {
    "just return foo" in {
      MatchAll and MatchNone shouldBe MatchNone
      MatchAll and ("a" === "b").asMatcher shouldBe ("a" === "b").asMatcher
    }
  }
  "MatchAll or foo" should {
    "return MatchAll" in {
      MatchAll or MatchNone shouldBe MatchAll
      MatchAll or ("a" === "b").asMatcher shouldBe MatchAll
    }
  }
  "MatchAnd.Format" should {
    "encode and decode json" in {
      val and                         = MatchAnd(JPath("foo"), JPath("bar"))
      val json                        = MatchAnd.Format(and)
      val backAgain: Result[MatchAnd] = MatchAnd.Format.decodeJson(json)
      backAgain shouldBe Right(and)
    }
  }
}
