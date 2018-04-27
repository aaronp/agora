package lupin.pub.collate

import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually

class CollatingPublisherTest extends BaseFlowSpec with Eventually with GivenWhenThen {

  "CollatingPublisher" should {
    "publish values from all its publishers" in {
      Given("A collating publisher")
      val cp = CollatingPublisher[Int, Int]()
      cp.subscribers() shouldBe empty

      And("some publishers")
      val first = Publishers.of(1, 2, 3)
      val second = Publishers.of(100, 200, 300, 400)
      val third = Publishers.of(1000, 2000)

      When("The collating publisher subscribes to two publishers")
      first.subscribe(cp.newSubscriber(1))
      second.subscribe(cp.newSubscriber(2))

      Then("no elements should be sent")
      cp.subscribers().size shouldBe 2

      When("A subscriber requests elements from the subscriber")
      val list = new ListSubscriber[Int]
      cp.subscribe(list)

      Then("one of the upstream subscriptions should be requested from")
      list.request(1)

      val gotOne = eventually {
        list.received() match {
          case List(1) => true
          case List(100) => false
          case other => fail(s"either the first or second subscription element should be requested: $other")
            true
        }
      }

      list.request(1)
      val nextExpected = if (gotOne) {
        List(1, 100)
      } else {
        List(100, 1)
      }

      eventually {
        list.receivedInOrderReceived() shouldBe nextExpected
      }

      third.subscribe(cp.newSubscriber(123))
      list.request(3)

      eventually {
        list.receivedInOrderReceived() should contain allOf(2, 200, 1000)
      }
    }
  }
}
