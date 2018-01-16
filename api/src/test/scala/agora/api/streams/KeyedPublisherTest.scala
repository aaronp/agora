package agora.api.streams

import agora.BaseSpec
import agora.api.streams.ConsumerQueue.{HardLimit, Unbounded}
import agora.io.AlphaCounter

class KeyedPublisherTest extends BaseSpec {

  "KeyedPublisher.snapshot" should {
    "report the queue and requested amount from subscriptions" in {
      object Pub extends KeyedPublisher[String] {
        override type SubscriberKey = String
        val ids = AlphaCounter.from(100)

        override protected def nextId() = ids.next()

        override def newDefaultSubscriberQueue(): ConsumerQueue[String] = ConsumerQueue.withMaxCapacity(10)
      }

      val s1 = new ListSubscriber[String] with HasConsumerQueue[String] {
        override def consumerQueue: ConsumerQueue[String] = ConsumerQueue[String](None)
      }
      val s2 = new ListSubscriber[String] with HasConsumerQueue[String] {
        override def consumerQueue: ConsumerQueue[String] = ConsumerQueue.keepLatest(7)
      }
      val s3 = new ListSubscriber[String]
      Pub.subscribe(s1)
      Pub.subscribe(s3)

      s2.request(123)
      Pub.publish("first")

      Pub.subscribe(s2)
      Pub.publish("second")
      Pub.publish("third")

      Pub.snapshot() shouldBe PublisherSnapshot(Map("1d" -> SubscriberSnapshot(0, 1, Unbounded), "1e" -> SubscriberSnapshot(120, 0, HardLimit(10))))
    }
  }
}
