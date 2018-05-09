package lupin.pub.concat

import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.scalatest.concurrent.Eventually

class ConcatPublisherTest extends BaseFlowSpec {
  "CollatingPublisher.concat" should {
    "publish elements to multiple subscribers" in {
      val first  = Publishers.of(1, 2, 3)
      val second = Publishers.of(4, 5, 6)
      val six    = ConcatPublisher.concat(first, second)

      val sub1 = new ListSubscriber[Int]
      sub1.request(5)
      six.subscribe(sub1)
      sub1.isCompleted() shouldBe false

      val sub2 = new ListSubscriber[Int]
      sub2.request(1)
      six.subscribe(sub2)
      sub2.isCompleted() shouldBe false

      eventually {
        sub1.receivedInOrderReceived() shouldBe List(1, 2, 3, 4, 5)
      }

      eventually {
        sub2.receivedInOrderReceived() shouldBe List(1)
      }
    }
    "publish elements from both publishers in order" in {
      val first  = Publishers.of(1, 2, 3)
      val second = Publishers.of(4, 5, 6)
      val six    = ConcatPublisher.concat(first, second)

      val sub1 = new ListSubscriber[Int]
      sub1.request(10)
      six.subscribe(sub1)
      eventually {
        sub1.receivedInOrderReceived() shouldBe List(1, 2, 3, 4, 5, 6)
      }
      sub1.isCompleted() shouldBe true
    }
    "publish elements the same publisher twice" in {
      val three = Publishers.of(1, 2, 3)
      val six   = ConcatPublisher.concat(three, three)

      val sub = new ListSubscriber[Int]
      sub.request(10)
      six.subscribe(sub)
      eventually {
        sub.receivedInOrderReceived() shouldBe List(1, 2, 3, 1, 2, 3)
      }
      sub.isCompleted() shouldBe true

    }
    "concat concatenated publishers" in {
      val three  = Publishers.of(1, 2, 3)
      val twelve = ConcatPublisher.concat(ConcatPublisher.concat(three, three), ConcatPublisher.concat(three, three))

      val sub = new ListSubscriber[Int]
      sub.request(10)
      twelve.subscribe(sub)
      eventually {
        sub.receivedInOrderReceived() shouldBe List(1, 2, 3, 1, 2, 3, 1, 2, 3, 1)
      }
      sub.isCompleted() shouldBe false

    }
  }
}
