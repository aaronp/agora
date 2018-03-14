package agora.json

import agora.BaseJsonSpec
import io.circe.Json

class GteTest extends BaseJsonSpec {
  "Gte" should {
    "unmarshal from '\"gte\" : 5'" in {

      val gteJson   = json"""{"gte" : 5}"""
      val backAgain = gteJson.as[JPredicate]
      backAgain shouldBe Right(Gte(Json.fromInt(5)))
    }
  }
}
