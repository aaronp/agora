package agora.api.streams

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import agora.api.streams.BasePublisher.BasePublisherSubscription
import cats.Semigroup
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.util.control.NonFatal

/**
  * A publishing skeleton which exposes a 'publish' to push elements to subscribers
  * @tparam T
  */
trait BasePublisher[T] extends Publisher[T] with StrictLogging {

  private val ids               = new AtomicInteger(0)
  private val subscriptionsById = new ConcurrentHashMap[Int, BasePublisherSubscription[T]]()

  /** create a new queue for a subscription
    * @return a new consumer queue
    */
  def newQueue(): ConsumerQueue[T]

  def remove(id: Int): Unit = {
    subscriptionsById.remove(id)
  }

  protected def newSubscription(s: Subscriber[_ >: T]): BasePublisherSubscription[T] = {
    new BasePublisher.BasePublisherSubscription[T](ids.incrementAndGet(), this, s, newQueue())
  }

  override def subscribe(s: Subscriber[_ >: T]) = {
    val subscription = newSubscription(s)
    subscriptionsById.put(subscription.id, subscription)
    s.onSubscribe(subscription)
  }

  /**
    * Push an element onto the queue
    *
    * @param elem the element to publish
    */
  def publish(elem: T) = {
    logger.debug(s"Notifying ${subscriptionsById.size} subscriber(s) of $elem")
    val values = subscriptionsById.values().iterator()
    while (values.hasNext) {
      values.next().onElement(elem)
    }
  }

  protected def subscriberForId(id: Int): Option[BasePublisherSubscription[T]] = Option(subscriptionsById.get(id))
}

object BasePublisher extends StrictLogging {

  def apply[T](mkQueue: () => ConsumerQueue[T]) = {
    new BasePublisher[T] {
      override def newQueue() = mkQueue()
    }
  }

  def apply[T](maxCapacity: Int) = {
    new BasePublisher[T] {
      override def newQueue() = ConsumerQueue(maxCapacity)
    }
  }

  /**
    * Note: This BasePublisher will conflate messages ONLY AFTER A SUBSCRIBER SUBSCRIBES.
    *
    * Any message arriving BEFORE a subscription is made will NOT be sent
    *
    * @param initialValue
    * @tparam T
    * @return
    */
  def apply[T: Semigroup](initialValue: Option[T] = None) = {
    new BasePublisher[T] {
      override def newQueue() = ConsumerQueue(initialValue)
    }
  }

  private[streams] class BasePublisherSubscription[T](val id: Int,
                                                      val publisher: BasePublisher[T],
                                                      val subscriber: Subscriber[_ >: T],
                                                      queue: ConsumerQueue[T])
      extends Subscription {

    override def cancel(): Unit = publisher.remove(id)

    override def request(n: Long): Unit = {
      safely(s"on request of $n") {
        queue.request(n)
      }
    }

    def onElement(value: T) = {
      safely(s"on event $value") {
        queue.offer(value)
      }
    }

    def safely(desc: => String)(thunk: => List[T]): Unit = {
      try {
        val list = thunk
        list.foreach(subscriber.onNext)
      } catch {
        case NonFatal(e) =>
          try {
            logger.debug(s"Notifying subscriber of: $e")
            subscriber.onError(e)
          } catch {
            case NonFatal(subscriberError) =>
              logger.warn(s"Subscriber (re) threw error $e on $desc : $subscriberError")
          }
          cancel()
      }
    }
  }

}
