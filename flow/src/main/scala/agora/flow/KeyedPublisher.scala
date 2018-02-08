package agora.flow

import java.util.concurrent.atomic.AtomicLong

import agora.flow.KeyedPublisher.{Aux, KeyedPublisherSubscription}
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
trait KeyedPublisher[T] extends Publisher[T] with StrictLogging {
  self =>

  type SubscriberKey

  protected def nextId(): SubscriberKey

  private val subscriptionsById = new collection.mutable.HashMap[SubscriberKey, KeyedPublisherSubscription[SubscriberKey, T]]()

  /** @param s the subscriber to check
    * @return true if the subscriber
    */
  def containsSubscriber(s: Subscriber[_ >: T]): Boolean = {
    subscriptionsById.values.exists(_.subscriber == s)
  }

  /** create a new queue for a subscription
    *
    * @return a new consumer queue
    */
  def newDefaultSubscriberQueue(): ConsumerQueue[T]

  protected def useMinTakeNext: Boolean = false

  def remove(id: SubscriberKey): Unit = {
    logger.debug(s"$this removing($id)")
    subscriptionsById.remove(id)
  }

  protected def newSubscription(s: Subscriber[_ >: T]): KeyedPublisherSubscription[SubscriberKey, T] = {
    val queue = s match {
      case hasQ: HasConsumerQueue[T] => hasQ.consumerQueue
      case _ => newDefaultSubscriberQueue()
    }

    val id: SubscriberKey = s match {
      case hasKey: HasKey[SubscriberKey] =>
        val existingKey = hasKey.key
        require(!subscriptionsById.contains(existingKey), s"${existingKey} is already subscribed")
        existingKey
      case _ => nextId()
    }

    val aux: Aux[SubscriberKey, T] = self.asInstanceOf[Aux[SubscriberKey, T]]
    new KeyedPublisher.KeyedPublisherSubscription[SubscriberKey, T](toString, id, aux, s, queue)
  }

  def snapshot(): PublisherSnapshot[SubscriberKey] = {
    val map = subscriptionsById.mapValues(_.snapshot())
    PublisherSnapshot[SubscriberKey](map.toMap)
  }

  override def subscribe(s: Subscriber[_ >: T]) = {
    logger.debug(s"$this onSubscribe($s)")
    val subscription = newSubscription(s)
    subscriptionsById.put(subscription.id, subscription)
    s.onSubscribe(subscription)
  }

  /**
    * Callback function when a subscription invokes its 'request' (e.g. 'takeNext') method
    *
    * @param subscription
    * @param requested
    */
  protected def onRequestNext(subscription: KeyedPublisherSubscription[SubscriberKey, T], requested: Long): Long = {
    requested
  }

  /**
    * Push an element onto the queue
    *
    * @param elem the element to publish
    * @return the max requestd across all subscribers
    */
  def publish(elem: T): Unit = {
    val total = subscriptionsById.size
    logger.debug(s"$this notifying ${total} subscriber(s) of $elem")

    if (total > 1) {
      subscriptionsById.values.par.foreach(_.onElement(elem))
    } else {
      subscriptionsById.values.foreach(_.onElement(elem))
    }
  }

  /** send 'onComplete' to all subscriptions
    */
  def complete() = {
    logger.debug(s"$this notifying ${subscriptionsById.size} subscriber(s) of complete")
    val values = subscriptionsById.values.iterator
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

  protected def subscriptions() = subscriptionsById.values.iterator

  def subscriptionCount(): Int = subscriptionsById.size

  protected def subscriberForId(id: SubscriberKey): Option[KeyedPublisherSubscription[SubscriberKey, T]] = subscriptionsById.get(id)
}

object KeyedPublisher extends StrictLogging {

  type Aux[K, T] = KeyedPublisher[T] with PublisherSnapshotSupport[K] {type SubscriberKey = K}

  class KeyedPublisherSubscription[SubscriberKey, T](override val name: String,
                                                     val id: SubscriberKey,
                                                     val publisher: Aux[SubscriberKey, T],
                                                     val subscriber: Subscriber[_ >: T],
                                                     queue: ConsumerQueue[T])
    extends Subscription with HasName {

    private val totalRequested = new AtomicLong(0)
    private val totalReceived = new AtomicLong(0)
    private val totalPushed = new AtomicLong(0)

    def snapshot(): SubscriberSnapshot = {
      SubscriberSnapshot(name, totalRequested.get, totalPushed.get, totalReceived.get, requested, queue.buffered(), queue.limit())
    }

    override def toString = s"subscription $id to $name"

    override def cancel(): Unit = {
      logger.debug(s"$this cancelling subscription")
      publisher.remove(id)
    }

    override def request(n: Long): Unit = {
      totalRequested.addAndGet(n)
      safely(s"$this : on request of $n") {
        queue.request(n)
      }
      publisher.onRequestNext(this, queue.requested())
    }

    def onElement(value: T): Unit = {
      safely(s"$this : on event $value") {
        queue.offer(value)
      }
      totalPushed.incrementAndGet()
    }

    def requested() = queue.requested

    def safely(desc: => String)(thunk: => List[T]): Unit = {
      try {
        logger.debug(desc)
        val list = thunk
        val total = list.count { e =>
          subscriber.onNext(e)
          true
        }
        totalReceived.addAndGet(total)
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
