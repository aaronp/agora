package agora.api.streams

import agora.BaseSpec
import cats.syntax.option._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

class PublisherOpsTest extends BaseSpec {

  import PublisherOps.implicits._
  import PublisherOpsTest._

  implicit val jsonDelta = DataDiff.JsonDiffAsDeltas

  "PublisherOps.onDelta" should {
    "notify the listener only when values change" ignore {
      val publisher = BasePublisher[Data](10)

      var received = List[Data]()
      publisher.onDeltas(1) { data: Data =>
        received = data :: received
      }

      publisher.publish(Data(123, "x"))
      received shouldBe List(Data(123, "x"))

      publisher.publish(Data(123, "x"))
      received shouldBe List(Data(123, "x"))

      publisher.publish(Data(123, "y"))
      received shouldBe List(Data(123, "y"), Data(123, "x"))

    }
  }
  "PublisherOps.filter" should {
    "only publish values which pass the filter" in {
      val publisher = BasePublisher[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      publisher.filter(deltaSubscriber, 1) { json =>
        json == json"123"
      }

      publisher.publish(json"456")
      deltaSubscriber.received() shouldBe empty

      publisher.publish(json"123")
      deltaSubscriber.received() shouldBe List(json"123")

      withClue("we need to request another before receiving another update") {
        publisher.publish(json"123")
        deltaSubscriber.received() shouldBe List(json"123")
      }

      deltaSubscriber.request(1)
      deltaSubscriber.received() shouldBe List(json"123", json"123")

      // request another, but send some filtered out messages first
      deltaSubscriber.request(1)
      publisher.publish(json"1")
      publisher.publish(json"2")
      publisher.publish(json"3")
      deltaSubscriber.received() shouldBe List(json"123", json"123")

      publisher.publish(json"123")
      deltaSubscriber.received() shouldBe List(json"123", json"123", json"123")
    }
  }
  "PublisherOps.subscribeByKey" should {
    "publish json updates with their key field" in {
      val publisher = BasePublisher[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      publisher.subscribeToUpdates(deltaSubscriber, 0)

      val first = Data(1, "first", Data(2, "child", Data(string = "grandchild").some).some)

      publisher.publish(first.asJson)
      deltaSubscriber.received() shouldBe empty

    }
  }
  "PublisherOps.subscribeToUpdates" should {
    "publish json updates" in {
      val publisher = BasePublisher[Json](10)

      val deltaSubscriber = new ListSubscriber[Json]
      publisher.subscribeToUpdates(deltaSubscriber, 0)

      val first = Data(1, "first", Data(2, "child", Data(string = "grandchild").some).some)

      publisher.publish(first.asJson)
      deltaSubscriber.received() shouldBe empty

      deltaSubscriber.request(1)
      deltaSubscriber.received() shouldBe List(first.asJson)

      val second = Data(1, "first", Data(2, "child", Data(string = "renamed").some).some)
      publisher.publish(second.asJson)
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
      publisher.publish(second.asJson)
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
