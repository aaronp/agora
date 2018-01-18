package agora.api.exchange.observer

import agora.BaseSpec
import agora.api.Implicits._
import agora.api.exchange._

import scala.language.reflectiveCalls

/**
  * This test should cover all the observer behaviour. Other tests can then just test
  * the area they're interested in, such as delegating to other observers or connecting
  * over a websocket
  */
class ExchangeObserverTest extends BaseSpec {

  "ExchangeObserver.onMatch" should {
    "notify the observer when a match is made" in {

      val observer = new TestObserver
      val exchange = Exchange(observer)
      val jobAck   = exchange.subscribe(WorkSubscription.localhost(7777).withSubscriptionKey("the worker")).futureValue
      val job      = "match me!".asJob.withAwaitMatch(false).withId("the job")
      val workAck  = exchange.submit(job).futureValue

      withClue("the subscription has not yet pulled any work, so no match should've taken place") {
        observer.lastMatch().toList shouldBe empty
      }

      withClue("upon requesting work, that initial 1 work item should be immediately decremented to 0 as it is matched") {
        exchange.request("the worker", 1).futureValue shouldBe RequestWorkAck("the worker", 0, 0)
      }

      observer.lastMatch().map(_.matchedJob) shouldBe Some(job)
      observer.lastMatch().map(_.selection.map(_.subscriptionKey)) shouldBe Some(Seq("the worker"))
    }
  }
  "ExchangeObserver.onSubscriptionCancelled" should {
    "notify the observer when a work subscription is cancelled" in {

      val observer = new TestObserver
      val exchange = Exchange(observer)
      val ack      = exchange.subscribe(WorkSubscription.localhost(7777).withSubscriptionKey("meh")).futureValue

      exchange.cancelSubscriptions(ack.id, "unknown").futureValue.cancelledSubscriptions shouldBe Map(
        "meh"     -> true,
        "unknown" -> false
      )

      observer.lastCancelledSubscription().map(_.cancelledSubscriptionKeys) shouldBe Some(Set("meh"))

    }
  }

  "ExchangeObserver.onJobCancelled" should {
    "notify the observer when a job is cancelled" in {

      val observer = new TestObserver
      val exchange = Exchange(observer)
      val jobAck   = exchange.submit("meh".asJob.withAwaitMatch(false)).futureValue

      val cancelAck = exchange.cancelJobs(jobAck.id).futureValue
      cancelAck.cancelledJobs shouldBe Map(jobAck.id -> true)

      observer.lastCancelledJob().map(_.cancelledJobIds) shouldBe Some(Set(jobAck.id))

      val cancelAck2 = exchange.cancelJobs(jobAck.id).futureValue
      cancelAck2.cancelledJobs shouldBe Map(jobAck.id -> false)

      // verify we don't notify again
      observer.events.collect {
        case _: OnJobsCancelled => 1
      }.sum shouldBe 1

    }
  }
  "ExchangeObserver.onSubscriptionRequestCountChanged" should {
    "update related subscriptions when a subscription is increased" in {
      val observer = new TestObserver
      val exchange = Exchange(observer)
      val subA     = WorkSubscription.localhost(1234).withSubscriptionKey("A")
      val subB     = WorkSubscription.localhost(1234).withSubscriptionKey("B").withReferences(Set("A"))
      val subC     = WorkSubscription.localhost(1234).withSubscriptionKey("C").withReferences(Set("B"))
      val ackA     = exchange.subscribe(subA).futureValue
      val ackB     = exchange.subscribe(subB).futureValue
      val ackC     = exchange.subscribe(subC).futureValue

      exchange.request(ackA.id, 1).futureValue
      exchange.request(ackB.id, 10).futureValue
      exchange.request(ackC.id, 100).futureValue

      val changes = observer.eventsInTheOrderTheyWereReceived.collect {
        case OnSubscriptionRequestCountChanged(_, id, before, after) => (id, before, after)
      }
      changes should contain only (
        ("A", 0, 1),
        ("A", 1, 11),
        ("A", 11, 111)
      )
    }
    "be notified when subscription counts are updated" in {
      val observer = new TestObserver
      val exchange = Exchange(observer)
      val ack      = exchange.subscribe(WorkSubscription.localhost(1234)).futureValue

      exchange.request(ack.id, 1).futureValue

      observer.lastRequestCountChanged() should matchPattern {
        case Some(OnSubscriptionRequestCountChanged(_, ack.id, 0, 1)) =>
      }
    }

  }
  "ExchangeObserver.onSubscriptionUpdated" should {
    "not notify updates for unknown subscription updates" in {
      val observer     = new TestObserver
      val exchange     = Exchange(observer)
      val updateResult = exchange.updateSubscriptionDetails(UpdateSubscription("doesn't exist")).futureValue
      updateResult.oldDetails shouldBe empty
      updateResult.newDetails shouldBe empty

      observer.events shouldBe empty
    }
    "be notified when a subscription is updated" in {
      val before   = agora.api.time.now()
      val observer = new TestObserver
      val exchange = Exchange(observer)
      val ack      = exchange.subscribe(WorkSubscription.localhost(1234)).futureValue

      val update = UpdateSubscription.append(ack.id, "key", "value")
      // call the method under test to trigger an update
      val updateReply = exchange.updateSubscriptionDetails(update).futureValue

      val after                                                         = agora.api.time.now()
      val Some(OnSubscriptionUpdated(time, delta, Candidate(id, x, y))) = observer.lastUpdated()
      withClue(s"$before <= ${time} <= $after") {
        time.isBefore(before) shouldBe false
        time.isAfter(after) shouldBe false
      }
      update.delta shouldBe delta
      update.id shouldBe ack.id
    }
  }

}
