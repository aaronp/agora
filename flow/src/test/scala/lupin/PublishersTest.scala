package lupin

import org.scalatest.concurrent.Eventually

class PublishersTest extends BaseFlowSpec with Eventually {

  "Publishers.forList" should {
    "publish the values to all subscribers" in {
      val p = Publishers.forList(List(1, 2, 3, 4))

      val sub = new ListSubscriber[Int]
      p.subscribe(sub)

      sub.receivedInOrderReceived() shouldBe Nil
      sub.request(3)
      eventually {
        sub.receivedInOrderReceived() shouldBe List(1, 2, 3)

      }

      val sub2 = new ListSubscriber[Int]
      p.subscribe(sub2)
      sub2.receivedInOrderReceived() shouldBe Nil
      sub2.request(10)
      eventually {
        sub2.receivedInOrderReceived() shouldBe List(1, 2, 3, 4)
        sub2.isCompleted() shouldBe true
      }

      sub.receivedInOrderReceived() shouldBe List(1, 2, 3)
      sub.isCompleted() shouldBe false
    }
  }

}
