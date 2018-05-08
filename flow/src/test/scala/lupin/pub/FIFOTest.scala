package lupin.pub

import cats.kernel.Semigroup
import lupin.BaseFlowSpec

import scala.concurrent.Future

class FIFOTest extends BaseFlowSpec {

  "FIFO.apply" should {
    "block on pop" in {
      val queue = FIFO[String](3)
      queue.enqueue("first") shouldBe true
      queue.enqueue("second") shouldBe true
      queue.enqueue("third") shouldBe true
      queue.enqueue("fourth") shouldBe false

      queue.pop() shouldBe "first"
      queue.pop() shouldBe "second"
      queue.pop() shouldBe "third"

      // now try to pop another value -- it should block
      @volatile var done = ""
      @volatile var called = false
      Future {
        called = true
        done = queue.pop()
      }

      eventually {
        called shouldBe true
      }
      Thread.sleep(10)
      done shouldBe ""

      // finally push a value to unblock our thread
      queue.enqueue("finished")
      eventually {
        done shouldBe "finished"
      }
    }
  }
  "FIFO.collated" should {
    "block on pop" in {
      implicit object ListSG extends Semigroup[List[Int]] {
        override def combine(x: List[Int], y: List[Int]): List[Int] = x ::: y
      }
      val ints: FIFO[Option[List[Int]]] = FIFO.collated
      ints.enqueue(Option(List(1))) shouldBe true
      ints.enqueue(Option(List(2, 3))) shouldBe true
      ints.enqueue(Option(Nil)) shouldBe true
      ints.enqueue(Option(List(4))) shouldBe true

      ints.pop() shouldBe Option(List(1, 2, 3, 4))

      // now try to pop another value -- it should block
      @volatile var done = Option.empty[List[Int]]
      @volatile var called = false
      Future {
        called = true
        done = ints.pop()
      }

      eventually {
        called shouldBe true
      }
      Thread.sleep(10)
      done shouldBe None

      // finally push a value to unblock our thread
      ints.enqueue(Option(List(100)))
      eventually {
        done shouldBe Option(List(100))
      }
    }
  }
  "FIFO.mostRecent" should {
    "set the last value" in {
      val fifo: FIFO[Option[String]] = FIFO.mostRecent[String]
      fifo.enqueue(Option("first")) shouldBe true
      fifo.enqueue(Option("second")) shouldBe true
      fifo.enqueue(Option("third")) shouldBe true

      fifo.pop() shouldBe Option("third")

      // now try to pop another value -- it should block
      @volatile var done = Option.empty[String]
      @volatile var called = false
      Future {
        called = true
        done = fifo.pop()
      }

      eventually {
        called shouldBe true
      }
      Thread.sleep(10)
      done shouldBe None

      // finally push a value to unblock our thread
      fifo.enqueue(Option("complete"))
      eventually {
        done shouldBe Option("complete")
      }
    }
  }

}
