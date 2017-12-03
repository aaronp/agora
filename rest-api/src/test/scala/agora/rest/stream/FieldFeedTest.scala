package agora.rest.stream

import agora.BaseSpec
import agora.api.json.JType

class FieldFeedTest extends BaseSpec {
  "FieldFeed" should {
    "notify of new paths" in {
      var fieldUpdates = Vector[Update]()
      val feed = DeltaFlow.FieldFeed { update =>
        fieldUpdates = update +: fieldUpdates
      }
      type Update = Vector[(List[String], JType)]

      feed.myPublisher.publish(json"""{ "x" : 123 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      feed.myPublisher.publish(json"""{ "x" : 456 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      feed.myPublisher.publish(json"""{ "y" : { "z" : 123} }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num, List("y", "z") -> JType.Num)
    }
  }
}
