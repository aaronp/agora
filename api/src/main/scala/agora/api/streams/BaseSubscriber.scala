package agora.api.streams

import org.reactivestreams.{Subscriber, Subscription}

/**
  * A base subscriber
  *
  * @tparam T
  */
class BaseSubscriber[T] extends Subscriber[T] {

  private var _subscriptionOption: Option[Subscription] = None
  private var initiallyRequested                        = 0L

  def subscriptionOption: Option[Subscription] = _subscriptionOption

  def subscription() = subscriptionOption.getOrElse(sys.error("Subscription not set"))

  override def onError(t: Throwable) = {
    throw t
  }

  override def onComplete() = {}

  override def onNext(t: T) = {}

  def request(n: Long = 1) = subscriptionOption match {
    case None    => initiallyRequested = initiallyRequested + n
    case Some(s) => s.request(n)
  }

  override def onSubscribe(s: Subscription) = {
    require(_subscriptionOption.isEmpty, "subscriptionOption already set")
    _subscriptionOption = Option(s)
    if (initiallyRequested > 0) {
      s.request(initiallyRequested)
      initiallyRequested = 0
    }
  }
}
