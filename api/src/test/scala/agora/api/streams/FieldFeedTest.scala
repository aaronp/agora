package agora.api.streams

import agora.BaseApiSpec
import agora.json.{JType, TypesByPath}
import agora.flow.{AsConsumerQueue, BaseProcessor, BaseSubscriber}
import io.circe.Json

class FieldFeedTest extends BaseApiSpec {
  "FieldFeed" should {
    "notify of new paths" in {
      import AsConsumerQueue._

      var fieldUpdates = Vector[TypesByPath]()

      val feed = FieldFeed(MaxCapacity[TypesByPath](10))
      feed.pathPublisher.subscribe(BaseSubscriber[TypesByPath](1) {
        case (_, update) => fieldUpdates = update +: fieldUpdates
      })

      val myPublisher = BaseProcessor.withMaxCapacity[Json](100)
      feed.request(3)
      myPublisher.subscribe(feed)

      myPublisher.publish(json"""{ "x" : 123 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      myPublisher.publish(json"""{ "x" : 456 }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num)

      myPublisher.publish(json"""{ "y" : { "z" : 123} }""")
      feed.fields shouldBe Vector(List("x") -> JType.Num, List("y", "z") -> JType.Num)
    }
  }
}
