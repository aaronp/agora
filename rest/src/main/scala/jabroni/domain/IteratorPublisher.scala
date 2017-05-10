package jabroni.domain

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

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
    extends Subscription
      with StrictLogging {
    override def cancel(): Unit = {
      if (consumeOnCancel) {
        logger.info(s"Cancelling subscription, consumeOnCancel is $consumeOnCancel with maxLimitToConsumeOnCancel $maxLimitToConsumeOnCancel")
        // exhaust the remaining ... hopefully it's not infinite!
        val limitedIterator = maxLimitToConsumeOnCancel.map(iterator.take).getOrElse(iterator)
        val remaining = limitedIterator.size
        if (limitedIterator.hasNext) {
          logger.warn(s"Tried to exhaust the remaining elements on cancel. We read $remaining, but there are more! maxLimitToConsumeOnCancel is $maxLimitToConsumeOnCancel")
        }
      } else {
        logger.info(s"Subscription cancelled (iterator may not be exhausted! Set consumeOnCancel if you fancy it next time)")
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
