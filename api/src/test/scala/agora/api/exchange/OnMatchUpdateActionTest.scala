package agora.api.exchange

import agora.BaseSpec
import io.circe.Json
import io.circe.syntax._
import agora.api.Implicits._
import agora.api.json.JPath

class OnMatchUpdateActionTest extends BaseSpec {
  "OnMatchUpdateAction" should {
    List[OnMatchUpdateAction](
      OnMatchUpdateAction.appendAction(Json.fromInt(6).asExpression, JPath("path", "to", "value")),
      OnMatchUpdateAction.removeAction(JPath("removeMe"))
    ).foreach { expected =>
      s"marshal $expected to/from json" in {
        val json = expected.asJson
        println(json)
        json.as[OnMatchUpdateAction] shouldBe Right(expected)
      }
    }
  }
}
