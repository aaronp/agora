package jabroni.domain

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.util.control.NonFatal

/**
  * Provides a publisher view over summat which can make an iterator
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
                                     consumeOnCancel: Boolean)
    extends Subscription
      with StrictLogging {
    override def cancel(): Unit = {
      if (consumeOnCancel) {
        logger.info(s"Cancelling subscription, consumeOnCancel is $consumeOnCancel")
        // exhaust the remaining ... hopefully it's not infinite!
        val remaining = iterator.take(1000).size
        logger.info(s"Exhausted $remaining ")
      } else {
        logger.info(s"Subscription cancelled")
      }
    }

    override def request(n: Long): Unit = {
      iterator.take(n.toInt).foreach { x =>
        try {
          s.onNext(x)
        } catch {
          case NonFatal(e) =>
            s.onError(e)
        }
      }
    }
  }

}
