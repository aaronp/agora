package agora.api.streams

import agora.BaseSpec
import cats.kernel.Semigroup

class ConsumerQueueTest extends BaseSpec {

  "ConsumerQueue.keepLatest" should {
    "just keep the most recent elements when set to 1" in {
      case class Foo(x: Int)
      val q = ConsumerQueue.keepLatest[Foo](1)
      q.offer(Foo(1)) shouldBe Nil
      q.offer(Foo(2)) shouldBe Nil

      q.requested() shouldBe 0L

      q.request(3) shouldBe List(Foo(2))
      q.requested() shouldBe 2L

      q.offer(Foo(3)) shouldBe List(Foo(3))
      q.requested() shouldBe 1L
      q.offer(Foo(4)) shouldBe List(Foo(4))
      q.requested() shouldBe 0L

      q.request(1) shouldBe Nil
      q.offer(Foo(5)) shouldBe List(Foo(5))
      q.offer(Foo(6)) shouldBe Nil
      q.offer(Foo(7)) shouldBe Nil
      q.requested() shouldBe 0L

      q.request(1) shouldBe List(Foo(7))
      q.requested() shouldBe 0L
    }
    "just keep the most recent N elements" in {
      val q = ConsumerQueue.keepLatest[String](2)
      q.offer("1st") shouldBe Nil
      q.offer("2nd") shouldBe Nil
      q.offer("3rd") shouldBe Nil
      q.offer("4th") shouldBe Nil

      q.requested() shouldBe 0L

      q.request(3) shouldBe List("3rd", "4th")
      q.requested() shouldBe 1L

      q.offer("5th") shouldBe List("5th")
      q.requested() shouldBe 0L

      q.request(1) shouldBe Nil
      q.offer("6th") shouldBe List("6th")

      q.offer("7th") shouldBe Nil
      q.offer("8th") shouldBe Nil
      q.offer("9th") shouldBe Nil
      q.offer("10th") shouldBe Nil
      q.requested() shouldBe 0L

      q.request(1) shouldBe List("9th")
      q.requested() shouldBe 0L

      q.request(1) shouldBe List("10th")
      q.requested() shouldBe 0L

      q.request(1) shouldBe Nil
      q.requested() shouldBe 1L
    }
  }

  "ConsumerQueue.instance" should {

    "return only one at a time when requested of a full queue" in {
      val q = ConsumerQueue.withMaxCapacity[String](2)
      q.offer("one") shouldBe Nil
      q.offer("two") shouldBe Nil
      q.requested() shouldBe 0L

      q.request(1) shouldBe List("one")
      q.requested() shouldBe 0L

      q.request(1) shouldBe List("two")
      q.requested() shouldBe 0L

      q.request(1) shouldBe Nil
    }
    "throw an exception when the queue exceeds the max size" in {
      val q = ConsumerQueue.withMaxCapacity[String](2)
      q.offer("1st ") shouldBe Nil
      q.offer("2nd ") shouldBe Nil
      val bng = intercept[IllegalStateException] {
        q.offer("3rd ")
      }
      bng.getMessage shouldBe "Queue full"
    }

    "update the number requested" in {
      val q = ConsumerQueue.withMaxCapacity[String](10)
      q.offer("1st ") shouldBe Nil
      q.offer("2nd ") shouldBe Nil
      q.requested() shouldBe 0L
      q.request(4) shouldBe List("1st ", "2nd ")
      q.requested() shouldBe 2L
      q.offer("3rd ") shouldBe List("3rd ")
      q.requested() shouldBe 1L
      q.offer("4th ") shouldBe List("4th ")
      q.requested() shouldBe 0L

      q.offer("5th ") shouldBe Nil
      q.offer("6th ") shouldBe Nil
      q.offer("7th ") shouldBe Nil
      q.requested() shouldBe 0L
      q.request(1) shouldBe List("5th ")
      q.requested() shouldBe 0L
    }
  }
  "ConflatingQueue.requested" should {

    "combine the elements based on the semigroup instance" in {
      implicit object AddSemigroup extends Semigroup[Int] {
        override def combine(x: Int, y: Int): Int = x + y
      }
      val q = ConsumerQueue[Int](Option(10))
      withClue("nothing is requested yet") {
        q.offer(1) shouldBe Nil // conflate to 11
        q.offer(2) shouldBe Nil // 11 + 2 = 13
      }

      q.requested() shouldBe 0L

      q.request(3) shouldBe List(13)
      q.requested() shouldBe 2L

      q.offer(3) shouldBe List(13 + 3)
      q.requested() shouldBe 1L
      q.offer(4) shouldBe List(20)
      q.requested() shouldBe 0L

      q.request(1) shouldBe Nil
      q.offer(1) shouldBe List(21)
      q.offer(1) shouldBe Nil // 22
      q.offer(1) shouldBe Nil // 23
      q.requested() shouldBe 0L

      q.request(1) shouldBe List(23)
      q.requested() shouldBe 0L
    }
    "represent the number of items requested" in {
      import cats.instances.string._

      val q = ConsumerQueue[String](None)
      q.requested() shouldBe 0L
      q.request(1) shouldBe Nil

      q.offer("1st ") shouldBe List("1st ")
      q.requested() shouldBe 0L

      q.offer("2nd ") shouldBe Nil
      q.request(1) shouldBe List("1st 2nd ")
      q.requested() shouldBe 0L

      q.request(2) shouldBe Nil
      q.requested() shouldBe 2L

      q.request(1) shouldBe Nil
      q.requested() shouldBe 3L

      q.offer("3rd ") shouldBe List("1st 2nd 3rd ")
      q.requested() shouldBe 2L

      q.offer("4th ") shouldBe List("1st 2nd 3rd 4th ")
      q.requested() shouldBe 1L
    }
  }
}
