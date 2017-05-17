package jabroni.domain

import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.language.existentials
import scala.util.control.NonFatal

/**
  * Provides a publisher view over summat which can make an iterator
  *
  * @param mkIter
  * @param consumeOnCancel
  * @tparam T
  */
class IteratorPublisher[T](mkIter: () => Iterator[T], consumeOnCancel: Boolean = true) extends Publisher[T] {
  override def subscribe(subscriber: Subscriber[_ >: T]): Unit = {
    val subscription = IteratorPublisher.IteratorSubscription(subscriber, mkIter(), consumeOnCancel)
    subscriber.onSubscribe(subscription)
  }
}

object IteratorPublisher {

  case class IteratorSubscription[T](s: Subscriber[_ >: T],
                                     iterator: Iterator[T],
                                     consumeOnCancel: Boolean,
                                     maxLimitToConsumeOnCancel: Option[Int] = Option(10000))
    extends Subscription {
    override def cancel(): Unit = {
      if (consumeOnCancel) {
        // exhaust the remaining ... hopefully it's not infinite!
        val limitedIterator = maxLimitToConsumeOnCancel.map(iterator.take).getOrElse(iterator)
        limitedIterator.size
      }
    }

    override def request(n: Long): Unit = {
      try {
        iterator.take(n.toInt).foreach { x =>
          s.onNext(x)
        }
      } catch {
        case NonFatal(e) =>
          s.onError(e)
      }
    }
  }

}
