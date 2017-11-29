package agora.api.streams

import agora.BaseSpec
import cats.instances.int._
import org.scalatest.concurrent.Eventually

class SemigroupPublisherTest extends BaseSpec with Eventually {
  "SemigroupPublisher.subscribe" should {
    "not receive elements once cancelled" in {
      val pub = SemigroupPublisher[Int](3)

      object Consume extends ListSubscriber[Int]

      Consume.request(10)
      pub.subscribe(Consume)

      Consume.received() shouldBe empty

      pub.publish(1)
      Consume.received() shouldBe List(1)

      // call the method under test
      Consume.subscription().cancel()

      pub.publish(2)
      Consume.received() shouldBe List(1)
    }
    "notify elements as soon as they are published when elements are already requested" in {
      val pub = SemigroupPublisher[Int](3)

      object Consume extends ListSubscriber[Int]

      pub.subscribe(Consume)
      Consume.request(2)

      Consume.received() shouldBe empty

      pub.publish(1)
      Consume.received() shouldBe List(1)

      pub.publish(2)
      Consume.received() shouldBe List(2, 1)

      withClue("we had originally only requested 2 elements, so shouldn't yet see the third") {
        pub.publish(3)
        Consume.received() shouldBe List(2, 1)
      }
    }
    "not notify until elements are requested" in {
      val pub = SemigroupPublisher[Int](3)

      object Consume extends ListSubscriber[Int]

      pub.subscribe(Consume)
      pub.publish(1)

      Consume.received() shouldBe empty
      Consume.request(1)

      Consume.received shouldBe List(1)
    }
    "notify new subscriptions with the state-of-the-world when they first subscribe" in {
      val pub = SemigroupPublisher[Int](3)
      // start off w/ a couple already published (to nobody)
      pub.publish(1)
      pub.publish(2)

      object First  extends ListSubscriber[Int]
      object Second extends ListSubscriber[Int]

      pub.subscribe(First)
      First.subscriptionOption.isDefined shouldBe true

      First.received shouldBe (empty)
      First.request(1)
      First.received should contain only (3)

      pub.publish(7)

      First.received should contain only (3)
      First.request(1)
      First.received should contain only (3, 7)

      pub.subscribe(Second)
      Second.request(2)
      Second.received() shouldBe List(10)

      pub.publish(10)

      Second.received() shouldBe List(10, 10)
      First.received should contain only (3, 7)
    }
  }
}
