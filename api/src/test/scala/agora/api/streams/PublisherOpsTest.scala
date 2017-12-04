package agora.api.streams

import agora.BaseSpec
import agora.api.data.DataDiff
import cats.syntax.option._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

class PublisherOpsTest extends BaseSpec {

  import PublisherOps.implicits._
  import PublisherOpsTest._

  implicit val jsonDelta = DataDiff.JsonDiffAsDeltas

  "PublisherOps.onDelta" should {
    "notify the listener only when values change" in {
      val publisher = BaseProcessor.withMaxCapacity[Data](10)

      var received = List[Data]()
      val subscriber = publisher.onDeltas { data: Data =>
        received = data :: received
      }
      subscriber.request(10)

      publisher.onNext(Data(123, "x"))
      received shouldBe List(Data(123, "x"))

      publisher.onNext(Data(123, "x"))
      received shouldBe List(Data(123, "x"))

      val change = Data(123, "y")
      publisher.onNext(change)
      received shouldBe List(Data(123, "y"), Data(123, "x"))

    }
  }
  "PublisherOps.filter" should {
    "only publish values which pass the filter" in {
      val publisher = BaseProcessor[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      deltaSubscriber.request(1)

      val ops = new PublisherOps[Json](publisher)

      publisher.filter(deltaSubscriber) { json =>
        json == json"123"
      }

      publisher.onNext(json"456")
      deltaSubscriber.received() shouldBe empty

      publisher.onNext(json"123")
      deltaSubscriber.received() shouldBe List(json"123")

      withClue("we need to request another before receiving another update") {
        publisher.onNext(json"123")
        deltaSubscriber.received() shouldBe List(json"123")
      }

      deltaSubscriber.request(1)
      deltaSubscriber.received() shouldBe List(json"123", json"123")

      // request another, but send some filtered out messages first
      deltaSubscriber.request(1)
      publisher.onNext(json"1")
      publisher.onNext(json"2")
      publisher.onNext(json"3")
      deltaSubscriber.received() shouldBe List(json"123", json"123")

      publisher.onNext(json"123")
      deltaSubscriber.received() shouldBe List(json"123", json"123", json"123")
    }
  }
  "PublisherOps.subscribeByKey" should {
    "publish json updates with their key field" in {
      val publisher = BaseProcessor[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      publisher.subscribeToUpdateDeltas(deltaSubscriber)

      val first = Data(1, "first", Data(2, "child", Data(string = "grandchild").some).some)

      publisher.onNext(first.asJson)
      deltaSubscriber.received() shouldBe empty

    }
  }
  "PublisherOps.subscribeToUpdates" should {
    "publish json updates" in {
      val publisher = BaseProcessor.withMaxCapacity[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      publisher.subscribeToUpdateDeltas(deltaSubscriber)

      val first = Data(1, "first", Data(2, "child", Data(string = "grandchild").some).some)

      publisher.onNext(first.asJson)
      deltaSubscriber.received() shouldBe empty

      deltaSubscriber.request(1)
      deltaSubscriber.received() shouldBe List(first.asJson)

      val second  = Data(1, "first", Data(2, "child", Data(string = "renamed").some).some)
      val updated = second.asJson
      publisher.onNext(updated)
      deltaSubscriber.request(1)

      val List(delta, sameAgain) = deltaSubscriber.received()

      sameAgain shouldBe first.asJson
      delta shouldBe
        json"""{
        "deltas" : [
          {
            "path" : ["child","child","string"],
            "lhs" : "grandchild",
            "rhs" : "renamed"
          }
        ]
      }"""

      // publish a message w/ no changes -- we should NOT get a delta message
      publisher.onNext(second.asJson)
      deltaSubscriber.request(1)

      val noUpdate = deltaSubscriber.received()
      withClue(s"A published message w/ no changes should not notify a subscriber: $noUpdate") {
        noUpdate.size shouldBe 2
      }
    }
  }
}

object PublisherOpsTest {

  case class Data(integer: Int = 0, string: String = "", child: Option[Data] = None) {
    def withChild(x: Data) = copy(child = Option(x))
  }

}
