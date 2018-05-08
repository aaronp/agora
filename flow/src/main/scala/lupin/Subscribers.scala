package lupin

import lupin.sub.{BaseSubscriber, DelegateSubscriber}
import org.reactivestreams.Subscriber

object Subscribers {

  def fold[A, B](initial: A)(f: (A, B) => A): Subscriber[B] = {
    val sub = BaseSubscriber[A](1) {
      case (sub, next) => sub.request(1)
    }
    fold(sub, initial)(f)
  }

  def fold[A, B](underlying: Subscriber[A], initial: A)(f: (A, B) => A): Subscriber[B] = {
    var state = initial
    contraMap[A, B](underlying) { next =>
      state = f(state, next)
      state
    }
  }

  def contraMap[A, B](underlying: Subscriber[A])(f: B => A): Subscriber[B] = {
    new DelegateSubscriber[B](underlying) {
      override def toString = s"contra-mapped $underlying"

      override def onNext(t: B): Unit = underlying.onNext(f(t))
    }
  }

}
