package lupin.pub.join

import lupin.BaseFlowSpec
import lupin.pub.FIFO
import org.scalatest.GivenWhenThen

import scala.concurrent.{Await, Future}

class CombineQueueTest extends BaseFlowSpec with GivenWhenThen {

  "CombineQueue" should {
    "send BothUpdate values when both sides have values" in {

      Given("A CombineQueue with equal left and right elements")
      val queueUnderTest: CombineQueue[Int, String] = newQ
      queueUnderTest.enqueue(Option(LeftUpdate(1))) shouldBe true
      queueUnderTest.enqueue(Option(LeftUpdate(2))) shouldBe true
      queueUnderTest.enqueue(Option(LeftUpdate(3))) shouldBe true

      queueUnderTest.enqueue(Option(RightUpdate("one"))) shouldBe true
      queueUnderTest.enqueue(Option(RightUpdate("two"))) shouldBe true
      queueUnderTest.enqueue(Option(RightUpdate("three"))) shouldBe true

      When("The elements are popped")
      Then("They should match up as 'BothUpdated' values")
      queueUnderTest.pop() shouldBe Option(BothUpdated(1, "one"))
      queueUnderTest.pop() shouldBe Option(BothUpdated(2, "two"))
      queueUnderTest.pop() shouldBe Option(BothUpdated(3, "three"))

      When("Another element is attempted to be popped")
      @volatile var running = false
      val nextFuture = Future {
        running = true
        queueUnderTest.pop()
      }
      eventually {
        running shouldBe true
      }
      Then("it should block")
      nextFuture.isCompleted shouldBe false

      When("A None value is enqueued")
      queueUnderTest.enqueue(None) shouldBe true

      Then("the pop should complete")
      Await.result(nextFuture, testTimeout) shouldBe None
    }
    "send updates when a new value is received" in {

      Given("A CombineQueue with equal left and right elements")
      val queueUnderTest: CombineQueue[Int, String] = newQ

      When("The elements are enqueued and popped evenly")
      Then("we should get elements as they come")
      queueUnderTest.enqueue(Option(LeftUpdate(1))) shouldBe true
      queueUnderTest.enqueue(Option(LeftUpdate(2))) shouldBe true

      queueUnderTest.pop() shouldBe Option(LeftUpdate(1))
      queueUnderTest.pop() shouldBe Option(LeftUpdate(2))

      queueUnderTest.enqueue(Option(RightUpdate("one"))) shouldBe true
      queueUnderTest.enqueue(Option(RightUpdate("two"))) shouldBe true

      queueUnderTest.pop() shouldBe Option(RightUpdate("one"))
      queueUnderTest.pop() shouldBe Option(RightUpdate("two"))

      When("Two left updates and one right update are enqueued")
      queueUnderTest.enqueue(Option(LeftUpdate(3))) shouldBe true
      queueUnderTest.enqueue(Option(LeftUpdate(4))) shouldBe true
      queueUnderTest.enqueue(Option(RightUpdate("three"))) shouldBe true

      Then("A BothUpdated and LeftUpdate value should next be popped")
      queueUnderTest.pop() shouldBe Option(BothUpdated(3, "three"))
      queueUnderTest.pop() shouldBe Option(LeftUpdate(4))

      When("Another element is attempted to be popped")
      @volatile var running = false
      val nextFuture = Future {
        running = true
        queueUnderTest.pop()
      }
      eventually {
        running shouldBe true
      }
      Then("it should block")
      nextFuture.isCompleted shouldBe false

      When("A left element is enqueued")
      queueUnderTest.enqueue(Option(LeftUpdate(5))) shouldBe true

      Then("the pop should complete with a LeftUpdate")
      Await.result(nextFuture, testTimeout) shouldBe Option(LeftUpdate(5))
    }
  }

  def newQ = new CombineQueue[Int, String](FIFO[LeftUpdate[Int, String]](), FIFO[RightUpdate[Int, String]]())
}
