package agora.flow

import org.reactivestreams.{Publisher, Subscriber, Subscription}

object Publishers {

  def map[A, B](underlying: Publisher[A])(f: A => B): Publisher[B] = {
    new Publisher[B] {
      override def subscribe(mappedSubscriber: Subscriber[_ >: B]): Unit = {
        object WrapperForA extends Subscriber[A] {
          override def onSubscribe(sInner: Subscription): Unit = {
            mappedSubscriber.onSubscribe(sInner)
          }

          override def onNext(t: A): Unit = {
            mappedSubscriber.onNext(f(t))
          }

          override def onError(t: Throwable): Unit = {
            mappedSubscriber.onError(t)
          }

          override def onComplete(): Unit = {
            mappedSubscriber.onComplete()
          }
        }
        underlying.subscribe(WrapperForA)
      }
    }
  }
}
