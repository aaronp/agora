package agora.api.exchange.observer

import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Subscriber, Subscription}

/**
  * A subscriber to exchange events
  *
  * @param initialSubscription
  * @param handler
  */
case class ExchangeSubscriber(initialSubscription: Int)(handler: ExchangeNotificationMessage => Unit)
    extends Subscriber[ExchangeNotificationMessage]
    with LazyLogging {
  private var subscriptionOpt: Option[Subscription] = None

  override def onError(t: Throwable) = {
    logger.error(s"onError($t)", t)
    cancel()
  }

  def cancel() = subscriptionOpt.fold(false) { s =>
    s.cancel()
    subscriptionOpt = None
    true
  }

  def request(n: Long) = subscriptionOpt.fold(false) { s =>
    s.request(n)
    true
  }

  override def onComplete() = {
    sys.error("The exchange has 'completed' somehow ... though an exchange represents an infinite data stream of jobs ?")
  }

  override def onNext(event: ExchangeNotificationMessage) = {
    handler(event)
    subscriptionOpt.foreach(_.request(1))
  }

  override def onSubscribe(s: Subscription) = {
    require(subscriptionOpt.isEmpty, "onSubscribe already called, as we already have a subscription!")
    subscriptionOpt = Option(s)
    request(initialSubscription)
  }
}

object ExchangeSubscriber {

  /**
    * Creates an [[ExchangeSubscriber]] from an ExchangeObserver
    *
    * @param obs
    * @param initialSubscription
    * @return an ExchangeSubscriber
    */
  def apply(obs: ExchangeObserver, initialSubscription: Int = 10): ExchangeSubscriber = {
    new ExchangeSubscriber(initialSubscription)(obs.onEvent _)
  }
}
