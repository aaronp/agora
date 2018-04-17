package agora.flow

import org.reactivestreams.{Subscriber, Subscription}

object Subscribers {

  def map[A, B](underlying: Subscriber[A])(f: B => A): Subscriber[B] = {
    new Subscriber[B] {
      override def onSubscribe(s: Subscription): Unit = {
        underlying.onSubscribe(s)
      }

      override def onNext(t: B): Unit = underlying.onNext(f(t))

      override def onError(t: Throwable): Unit = underlying.onError(t)

      override def onComplete(): Unit = underlying.onComplete()
    }
  }

}
