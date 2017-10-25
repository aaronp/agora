package agora.api.exchange.observer

import agora.BaseSpec
import agora.api.exchange.{Candidate, Exchange, UpdateSubscription, WorkSubscription}

class ExchangeObserverTest extends BaseSpec {
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
