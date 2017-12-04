package agora.api.streams

import agora.BaseSpec

class ConsumerQueueTest extends BaseSpec {

  "ConsumerQueue.instance" should {
    "throw an exception when the queue exceeds the max size" in {
      val q = ConsumerQueue[String](2)
      q.offer("1st ") shouldBe Nil
      q.offer("2nd ") shouldBe Nil
      val bng = intercept[IllegalStateException] {
        q.offer("3rd ")
      }
      bng.getMessage shouldBe "Queue full"
    }

    "update the number requestd" in {
      val q = ConsumerQueue[String](10)
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
