package agora.api.streams

import org.reactivestreams.{Subscriber, Subscription}

trait HasSubscriber[T] extends Subscriber[T] {

  protected def underlyingSubscriber: Subscriber[T]

  override def onError(t: Throwable) = underlyingSubscriber.onError(t)

  override def onComplete() = underlyingSubscriber.onComplete()

  override def onNext(t: T) = underlyingSubscriber.onNext(t)

  override def onSubscribe(s: Subscription) = underlyingSubscriber.onSubscribe(s)
}
