package agora.json

import agora.BaseJsonSpec
import AgoraJsonImplicits._
import io.circe.generic.auto._

class MatchAndTest extends BaseJsonSpec {
  "MatchAll and foo" should {
    "just return foo" in {
      MatchAll and MatchNone shouldBe MatchNone
      (MatchAll and ("a" === "b").asMatcher()) shouldBe (("a" === "b").asMatcher())
    }
  }
  "MatchAll or foo" should {
    "return MatchAll" in {
      MatchAll or MatchNone shouldBe MatchAll
      MatchAll or ("a" equalTo "b").asMatcher() shouldBe MatchAll
    }
  }
  "MatchAnd.Format" should {
    "encode and decode json" in {
      val and       = JPath("foo").asMatcher().and(JPath("bar"))
      val json      = and.json
      val backAgain = json.as[And]
      backAgain shouldBe Right(and)
    }
  }
}
