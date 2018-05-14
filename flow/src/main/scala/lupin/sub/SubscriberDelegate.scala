package lupin.sub

import org.reactivestreams.{Subscriber, Subscription}

abstract class SubscriberDelegate[T](underlying: Subscriber[_]) extends Subscriber[T] {
  override def onError(t: Throwable) = underlying.onError(t)

  override def onComplete() = underlying.onComplete()

  override def onSubscribe(s: Subscription) = underlying.onSubscribe(s)
}
