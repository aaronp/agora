package agora.rest.stream

import agora.BaseSpec
import agora.api.json.JType

class FieldFeedTest extends BaseSpec {
  "FieldFeed" should {
    "notify of new paths" in {
      val feed = new DeltaFlow.FieldFeed.JsonFeed
      type Update = Vector[(List[String], JType)]
      var fieldUpdates = Vector[Update]()
      feed.onNewFields { update =>
        fieldUpdates = update +: fieldUpdates
      }

      feed.myPublisher.publish(json"""{ "x" : 123 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      feed.myPublisher.publish(json"""{ "x" : 456 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      feed.myPublisher.publish(json"""{ "y" : { "z" : 123} }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num, List("y", "z") -> JType.Num)
    }
  }
}
