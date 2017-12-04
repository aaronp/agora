package agora.api.streams

import agora.BaseSpec
import io.circe.Json

import scala.concurrent.duration._

class DataFeedDslTest extends BaseSpec {
  "DataFeed.asJson" should {
    "produce json values" in {
      import io.circe.generic.auto._
      case class Meh(iLoveCirce: Boolean)
      val feed     = DataFeedDsl.withMaxCapacity[Meh](3)
      val listener = new ListSubscriber[Json]()
      feed.asJsonDsl.subscribe(listener)
      feed.onNext(Meh(true))

      listener.received() shouldBe Nil
      listener.request(1)
      listener.received() shouldBe List(Json.obj("iLoveCirce" -> Json.fromBoolean(true)))
    }
    "manual throughput entrypoint" ignore {
      val feed = DataFeedDsl.withMaxCapacity[String](3)
      feed.onNext("hi")

      val listener1 = new ListSubscriber[String]()
      feed.subscribe(listener1)

      (0 to 10).foreach { _ =>
        val deadline = 1.second.fromNow
        var count    = 0
        while (deadline.hasTimeLeft()) {
          feed.onNext("value")
          //          listener1.request(1)
          //          listener1.clear()
          count = count + 1
        }
        val size = listener1.received().size
        println(size + " received")
        println(count + " published")
      }

    }
  }
}
