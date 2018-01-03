package agora.api.streams

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import agora.api.streams.BasePublisher.BasePublisherSubscription
import cats.Semigroup
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.util.control.NonFatal

/**
  * A publishing skeleton which exposes a 'publish' to push elements to subscribers.
  *
  * As each subscriber's subscription will be 'taking next' independently,
  * we'll also want to potentially propagate 'takeNext' calls which can be used
  * for subscriptions which are feeding us.
  *
  * @tparam T
  */
trait BasePublisher[T] extends Publisher[T] with StrictLogging {

  private val ids               = new AtomicInteger(0)
  private val subscriptionsById = new ConcurrentHashMap[Int, BasePublisherSubscription[T]]()

  /** create a new queue for a subscription
    *
    * @return a new consumer queue
    */
  def newDefaultSubscriberQueue(): ConsumerQueue[T]

  protected def useMinTakeNext: Boolean = false

  def remove(id: Int): Unit = {
    logger.debug(s"$this removing($id)")
    subscriptionsById.remove(id)
  }

  protected def newSubscription(s: Subscriber[_ >: T]): BasePublisherSubscription[T] = {
    val queue = s match {
      case hasQ: HasConsumerQueue[T] => hasQ.consumerQueue
      case _                         => newDefaultSubscriberQueue()
    }
    new BasePublisher.BasePublisherSubscription[T](toString, ids.incrementAndGet(), this, s, queue)
  }

  def isSubscribed(name: String) = subscriptionsById.contains(name)

  override def subscribe(s: Subscriber[_ >: T]) = {
    logger.debug(s"$this onSubscribe($s)")
    val subscription = newSubscription(s)
    subscriptionsById.put(subscription.id, subscription)
    s.onSubscribe(subscription)
  }

  // we just need to know what the max requested is for

  /**
    * Callback function when a subscription invokes its 'request' (e.g. 'takeNext') method
    *
    * @param subscription
    * @param requested
    */
  protected def onRequestNext(subscription: BasePublisherSubscription[T], requested: Long): Long = {
    requested
  }

  /**
    * Push an element onto the queue
    *
    * @param elem the element to publish
    * @return the max requestd across all subscribers
    */
  def publish(elem: T): Unit = {
    logger.debug(s"$this notifying ${subscriptionsById.size} subscriber(s) of $elem")
    val values = subscriptionsById.values().iterator()
    while (values.hasNext) {
      val subscriber = values.next()
      subscriber.onElement(elem)
    }
  }

  /** send 'onComplete' to all subscriptions
    */
  def complete() = {
    logger.debug(s"$this notifying ${subscriptionsById.size} subscriber(s) of complete")
    val values = subscriptionsById.values().iterator()
    while (values.hasNext) {
      values.next().subscriber.onComplete()
    }
    subscriptionsById.clear()
  }

  /** send 'onError' to all subscriptions and remove all subscribed
    */
  def completeWithError(err: Throwable) = {
    logger.debug(s"$this notifying ${subscriptionsById.size} subscriber(s) of $err")
    val values = subscriptions()
    while (values.hasNext) {
      values.next().subscriber.onError(err)
    }
    subscriptionsById.clear()
  }

  protected def subscriptions() = subscriptionsById.values().iterator()

  def subscriptionCount(): Int = subscriptionsById.size()

  protected def subscriberForId(id: Int): Option[BasePublisherSubscription[T]] = Option(subscriptionsById.get(id))
}

object BasePublisher extends StrictLogging {

  def apply[F[_], T](newQueueArgs: F[T])(implicit asQueue : AsConsumerQueue[F]) = {
    new BasePublisher[T] {
      override def newDefaultSubscriberQueue() = asQueue.newQueue(newQueueArgs)
    }
  }

  def apply[T](maxCapacity: Int) = {
    new BasePublisher[T] {
      override def newDefaultSubscriberQueue() = ConsumerQueue.withMaxCapacity(maxCapacity)
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
      override def newDefaultSubscriberQueue() = ConsumerQueue(initialValue)
    }
  }

  class BasePublisherSubscription[T](publisherName: String,
                                     val id: Int,
                                     val publisher: BasePublisher[T],
                                     val subscriber: Subscriber[_ >: T],
                                     queue: ConsumerQueue[T])
      extends Subscription {

    override def toString = s"subscription $id to $publisherName"

    override def cancel(): Unit = {
      logger.debug(s"$this cancelling subscription")
      publisher.remove(id)
    }

    override def request(n: Long): Unit = {
      safely(s"$this : on request of $n") {
        queue.request(n)
      }
      publisher.onRequestNext(this, queue.requested())
    }

    def onElement(value: T): Unit = {
      safely(s"$this : on event $value") {
        queue.offer(value)
      }
    }

    def requested() = queue.requested

    def safely(desc: => String)(thunk: => List[T]): Unit = {
      try {
        logger.debug(desc)
        val list = thunk
        list.foreach(subscriber.onNext)
      } catch {
        case NonFatal(e) =>
          try {
            logger.debug(s"$this Notifying subscriber $desc: $e")
            subscriber.onError(e)
          } catch {
            case NonFatal(subscriberError) =>
              logger.warn(s"$this  : Subscriber (re) threw error $e on $desc : $subscriberError")
          }
          cancel()
      }
    }
  }
}