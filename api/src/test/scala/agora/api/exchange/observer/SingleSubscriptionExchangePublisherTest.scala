package agora.api.exchange.observer

import agora.BaseApiSpec
import agora.api.Implicits._
import agora.api.exchange.QueueStateResponse
import org.reactivestreams.{Subscriber, Subscription}

class SingleSubscriptionExchangePublisherTest extends BaseApiSpec {
  "SingleSubscriptionExchangePublisher" should {
    "publish events to subscribers" in {
      val publisher = SingleSubscriptionExchangePublisher(ExchangeObserverDelegate())
      object subscriber extends Subscriber[ExchangeNotificationMessage] {
        var events = List[ExchangeNotificationMessage]()

        override def onError(t: Throwable) = throw t

        override def onComplete() = ???

        override def onNext(t: ExchangeNotificationMessage) = {
          events = t :: events
        }

        var subscription = Option.empty[Subscription]

        override def onSubscribe(s: Subscription) = {
          require(subscription.isEmpty)
          s.request(1)
          subscription = Option(s)
        }
      }
      publisher.subscribe(subscriber)

      val job = OnJobSubmitted(agora.time.now(), "hi".asJob)
      publisher.onJobSubmitted(job)
      subscriber.events.head shouldBe (job)
      subscriber.events.tail.head should matchPattern {
        case OnStateOfTheWorld(_, QueueStateResponse(Nil, Nil)) =>
      }
    }
  }
}
