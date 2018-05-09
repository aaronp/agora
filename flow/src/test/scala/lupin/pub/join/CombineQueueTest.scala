package lupin.pub.join

import lupin.BaseFlowSpec
import lupin.pub.FIFO
import org.scalatest.GivenWhenThen

import scala.concurrent.{Await, Future}

class CombineQueueTest extends BaseFlowSpec with GivenWhenThen {

  "CombineQueue" should {
    "send BothUpdate values when both sides have values" in {

      Given("A CombineQueue with equal left and right elements")
      val queueUnderTest = newQ
      queueUnderTest.leftQueue.enqueue(LeftUpdate(1))
      queueUnderTest.leftQueue.enqueue(LeftUpdate(2))
      queueUnderTest.leftQueue.enqueue(LeftUpdate(3))

      queueUnderTest.rightQueue.enqueue(RightUpdate("one"))
      queueUnderTest.rightQueue.enqueue(RightUpdate("two"))
      queueUnderTest.rightQueue.enqueue(RightUpdate("three"))

      When("The elements are popped")
      Then("They should match up as 'BothUpdated' values")
      queueUnderTest.pop() shouldBe BothUpdated(1, "one")
      queueUnderTest.pop() shouldBe BothUpdated(2, "two")
      queueUnderTest.pop() shouldBe BothUpdated(3, "three")

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
      queueUnderTest.leftQueue.enqueue(LeftUpdate(4))

      Then("the pop should complete with a LeftUpdate")
      Await.result(nextFuture, testTimeout) shouldBe LeftUpdate(4)
    }
  }

  def newQ = new CombineQueue[Int, String](FIFO[LeftUpdate[Int, String]](), FIFO[RightUpdate[Int, String]]())
}
