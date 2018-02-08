package agora.flow

import agora.flow.ConsumerQueue.{DiscardLimit, HardLimit, Unbounded}
import cats.Semigroup

class KeyedPublisherTest extends BaseFlowSpec {

  "KeyedPublisher.snapshot" should {
    "report the queue and requested amount from subscriptions" in {
      object Pub extends KeyedPublisher[String] {

        def anonymousSnapshot() = {
          snapshot().subscribers.mapValues(_.copy(name = ""))
        }
        override type SubscriberKey = String
        val ids = Iterator.from(0).map(_.toString)

        override protected def nextId() = ids.next()

        override def newDefaultSubscriberQueue(): ConsumerQueue[String] = ConsumerQueue.withMaxCapacity(10)
      }

      val s1 = new ListSubscriber[String] with HasConsumerQueue[String] {

        implicit object StringSemigroup extends Semigroup[String] {
          override def combine(x: String, y: String): String = x ++ y
        }

        override def consumerQueue: ConsumerQueue[String] = ConsumerQueue[String](None)
      }
      val s2 = new ListSubscriber[String] with HasConsumerQueue[String] {
        override def consumerQueue: ConsumerQueue[String] = ConsumerQueue.keepLatest(7)
      }

      // the first subscription will enqueue one element, as it never requests any elements
      Pub.subscribe(s1)
      Pub.anonymousSnapshot shouldBe Map("0" -> SubscriberSnapshot("", 0, 0, 0, 0, 0, Unbounded))
      Pub.publish("first")
      Pub.anonymousSnapshot shouldBe Map("0" -> SubscriberSnapshot("", 0, 1, 0, 0, 1, Unbounded))

      // the second subscription will ask for 123, then get sent 2, leaving 121 requested and 0 queued
      s2.request(123)
      Pub.subscribe(s2)
      Pub.anonymousSnapshot shouldBe
        Map("0" -> SubscriberSnapshot("", 0, 1, 0, 0, 1, Unbounded),
            "1" -> SubscriberSnapshot("", 123, 0, 0, 123, 0, DiscardLimit(7)))

      // s1 and s2 will see these, but s1 will conflate them into a single element
      Pub.publish("second")
      Pub.publish("third")

      // lastly we add a third w/ a fixed max capacity. It won't see any elements, but will request 4
      val s3 = new ListSubscriber[String] with HasConsumerQueue[String] {
        override def consumerQueue: ConsumerQueue[String] = ConsumerQueue.withMaxCapacity(9)
      }
      Pub.subscribe(s3)
      s3.request(4)
      Pub.anonymousSnapshot shouldBe Map(
          "0" -> SubscriberSnapshot("", 0, 3, 0, 0, 1, Unbounded),
          "1" -> SubscriberSnapshot("", 123, 2, 2, 121, 0, DiscardLimit(7)),
          "2" -> SubscriberSnapshot("", 4, 0, 0, 4, 0, HardLimit(9))
        )
    }
  }
}
