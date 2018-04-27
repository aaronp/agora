package lupin

import lupin.sub.DelegateSubscriber
import org.reactivestreams.Subscriber

object Subscribers {

  def fold[A, B](underlying: Subscriber[A], initial: A)(f: (A, B) => A): Subscriber[B] = {
    var state = initial
    contraMap[A, B](underlying) { next =>
      state = f(state, next)
      state
    }
  }

  def contraMap[A, B](underlying: Subscriber[A])(f: B => A): Subscriber[B] = {
    new DelegateSubscriber[B](underlying) {
      override def toString           = s"contra-mapped $underlying"
      override def onNext(t: B): Unit = underlying.onNext(f(t))
    }
  }

}
