package agora.api.streams

import org.reactivestreams.{Subscriber, Subscription}

/**
  * A base subscriber
  *
  * @tparam T
  */
abstract class BaseSubscriber[T](initialRequest: Long) extends Subscriber[T] {

  private var _subscriptionOption: Option[Subscription] = None
  private var initiallyRequested                        = initialRequest

  def subscriptionOption: Option[Subscription] = _subscriptionOption

  def contraMap[A](f: A => T): Subscriber[A] = {
    val self = this
    new DelegateSubscriber[A](self) {
      override def onNext(t: A) = self.onNext(f(t))
    }
  }

  def subscription() = subscriptionOption.getOrElse(sys.error("Subscription not set"))

  override def onError(t: Throwable): Unit = {
    throw t
  }

  override def onComplete(): Unit = {}

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
