package agora.api.json

import agora.BaseSpec
import io.circe.Json

class JRegexTest extends BaseSpec {
  "JRegex" should {
    "match a.* in json string 'aaa'" in {
      JRegex("a.*").matches(Json.fromString("a")) shouldBe true
      JRegex("a.*").matches(Json.fromString("abc")) shouldBe true
      JRegex("a.*").matches(Json.fromString("bc")) shouldBe false
    }
    "match a+ in json string 'aaa'" in {
      JRegex("a+").matches(Json.fromString("a")) shouldBe true
      JRegex("a+").matches(Json.fromString("b")) shouldBe false
    }
  }

}
