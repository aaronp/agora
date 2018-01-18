package agora.flow

import org.reactivestreams.{Subscriber, Subscription}

abstract class DelegateSubscriber[T](underlying: Subscriber[_]) extends Subscriber[T] {
  override def onError(t: Throwable) = underlying.onError(t)

  override def onComplete() = underlying.onComplete()

  override def onSubscribe(s: Subscription) = underlying.onSubscribe(s)
}
