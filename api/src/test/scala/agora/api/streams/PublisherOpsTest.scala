package agora.api.streams

import agora.BaseSpec
import cats.syntax.option._
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

class PublisherOpsTest extends BaseSpec {

  import PublisherOps._
  import PublisherOpsTest._

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
