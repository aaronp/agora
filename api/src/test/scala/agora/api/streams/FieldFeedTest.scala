package agora.rest.stream

import agora.BaseSpec
import agora.api.json.{JType, TypesByPath}
import agora.api.streams.{AsConsumerQueue, BaseProcessor, BasePublisher, BaseSubscriber}
import io.circe.Json

class FieldFeedTest extends BaseSpec {
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
