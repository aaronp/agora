package agora.rest.client

import agora.api.streams.BaseSubscriber
import org.reactivestreams.Subscription

trait ClientSubscription[T] {
  def onNext(subscription: Subscription, value: T): Unit
  def onError(subscription: Subscription, err: Throwable): Unit
  def onComplete(subscription: Subscription): Unit
}

object ClientSubscription {

  class Facade[T](delegate: ClientSubscription[T]) extends BaseSubscriber[T] {
    override def onError(t: Throwable): Unit = delegate.onError(subscription(), t)

    override def onComplete(): Unit = delegate.onComplete(subscription())

    override def onNext(value: T): Unit = delegate.onNext(subscription(), value)
  }

}
