package lupin.pub.collate

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.GivenWhenThen

class CollatingPublisherTest extends BaseFlowSpec with GivenWhenThen {

  "CollatingPublisher" should {
    "consume from all publishers even after some have completed" in {
      val cp = CollatingPublisher[Int, Int](fair = true)
      val first = Publishers.of(1, 2, 3, 4, 5, 6)
      val second = Publishers.of(100)
      val negativeInts = (-10 to -1).toList
      val third = Publishers.forValues(negativeInts)

      When("The collating publisher subscribes to two publishers")
      first.subscribe(cp.newSubscriber(1))
      second.subscribe(cp.newSubscriber(2))
      third.subscribe(cp.newSubscriber(3))

      val sub = new ListSubscriber[Int]
      cp.valuesPublisher.subscribe(sub)
      sub.request(Long.MaxValue)

      eventually {
        sub.receivedInOrderReceived() should contain allElementsOf (negativeInts ++ List(1, 2, 3, 4, 5, 6, 100))
      }

    }
    "only request elements when all are consumed" in {

      //consider the case where we subscribe to 3 publishers.
      // when one of our subscribers requests 1 element, we don't know which of the 3 will have an element ready,
      // so we have to send a request for 1 element from all three.
      // In that case, however, we could (and likely will) receive 3 elements. Our subscriber should only
      // get the one element it requested, however. What's more, when if we DO get 3 elements, we shouldn't need
      // to request any more elements from our upstream publishers if our subscriber requests 2 more (as we already
      // received them)

      Given("A collating publisher connected to three upstream publishers")
      val cp = CollatingPublisher[Int, Int](fair = false)
      class TestSubscription(subscriber: Subscriber[Int]) extends Subscription {
        var totalRequested = new AtomicLong(0)
        var cancelCalls = new AtomicInteger(0)
        subscriber.onSubscribe(this)

        def requested = totalRequested.get

        override def request(n: Long): Unit = totalRequested.addAndGet(n)

        override def cancel(): Unit = cancelCalls.incrementAndGet()
      }
      val sub1 = new TestSubscription(cp.newSubscriber(1))
      val sub2 = new TestSubscription(cp.newSubscriber(2))
      val sub3 = new TestSubscription(cp.newSubscriber(3))
      cp.subscribers() shouldBe Set(1, 2, 3)
      sub1.requested shouldBe 0
      sub2.requested shouldBe 0
      sub3.requested shouldBe 0

      When("A subscription requests one element")
      val subscriber = new ListSubscriber[(Int, Int)]
      cp.subscribe(subscriber)
      subscriber.request(1)

      Then("an element should be requested from all three upstream subscriptions")
      eventually {
        sub1.requested shouldBe 1
        sub2.requested shouldBe 1
        sub3.requested shouldBe 1
      }


      println("done")
    }
    "publish values from all its publishers" in {
      Given("A collating publisher")
      val cp = CollatingPublisher[Int, Int](fair = true)
      cp.subscribers() shouldBe empty

      And("some publishers")
      val first = Publishers.of(1, 2, 3)
      val second = Publishers.of(100, 200, 300, 400)
      val third = Publishers.of(1000, 2000)

      When("The collating publisher subscribes to two publishers")
      first.subscribe(cp.newSubscriber(-1))
      second.subscribe(cp.newSubscriber(-2))
      cp.subscribers() shouldBe Set(-1, -2)

      And("A subscriber requests elements from the subscriber")
      val list = new ListSubscriber[(Int, Int)]
      cp.subscribe(list)

      Then("one of the upstream subscriptions should be requested from")
      list.request(1)

      val gotOne = eventually {
        list.received() match {
          case List((-1, 1)) => true
          case List((-2, 100)) => false
          case other =>
            fail(s"either the first or second subscription element should be requested: $other")
            true
        }
      }

      When("another request is made, then the value from another publisher should be received")
      list.request(1)
      val nextExpected = if (gotOne) {
        List((-1, 1), (-2, 100))
      } else {
        List((-2, 100), (-1, 1))
      }

      eventually {
        list.receivedInOrderReceived() shouldBe nextExpected
      }

      third.subscribe(cp.newSubscriber(123))
      list.request(3)

      eventually {
        list.receivedInOrderReceived().map(_._2) should contain allOf(2, 200, 1000)
      }
    }
  }
}
