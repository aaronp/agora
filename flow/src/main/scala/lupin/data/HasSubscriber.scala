package lupin.data

import org.reactivestreams.{Subscriber, Subscription}

/**
  * This delegate mix-in allows for easily overriding a Subscriber implementation
  *
  * @tparam T the subscription type
  */
trait HasSubscriber[T] extends Subscriber[T] {

  protected def underlyingSubscriber: Subscriber[_ >: T]

  override def onError(t: Throwable) = underlyingSubscriber.onError(t)

  override def onComplete() = underlyingSubscriber.onComplete()

  override def onNext(t: T) = underlyingSubscriber.onNext(t)

  override def onSubscribe(s: Subscription) = underlyingSubscriber.onSubscribe(s)
}
