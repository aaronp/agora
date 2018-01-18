package agora.api.streams

import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

import agora.api.streams.KeyedPublisher.{Aux, KeyedPublisherSubscription}
import agora.io.core.HasKey
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

  private val subscriptionsById = new ConcurrentHashMap[SubscriberKey, KeyedPublisherSubscription[SubscriberKey, T]]()

  /** @param s the subscriber to check
    * @return true if the subscriber
    */
  def containsSubscriber(s : Subscriber[_ >: T]) : Boolean = {
    import scala.collection.JavaConverters._
    val jIter = subscriptionsById.values().iterator()
    jIter.asScala.map(_.subscriber).contains(s)
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
    import scala.collection.JavaConverters._
    val map = subscriptionsById.asScala.mapValues { subscriber =>
      subscriber.snapshot()
    }
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
    logger.debug(s"$this notifying ${subscriptionsById.size} subscriber(s) of $elem")
    val consumer = new Consumer[KeyedPublisherSubscription[SubscriberKey, T]] {
      override def accept(subscriber: KeyedPublisherSubscription[SubscriberKey, T]): Unit = subscriber.onElement(elem)
    }
    subscriptionsById.values().forEach(consumer)
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

  protected def subscriberForId(id: SubscriberKey): Option[KeyedPublisherSubscription[SubscriberKey, T]] = Option(subscriptionsById.get(id))
}

object KeyedPublisher extends StrictLogging {

  type Aux[K, T] = KeyedPublisher[T] {type SubscriberKey = K}

  class KeyedPublisherSubscription[SubscriberKey, T](publisherName: String,
                                                     val id: SubscriberKey,
                                                     val publisher: Aux[SubscriberKey, T],
                                                     val subscriber: Subscriber[_ >: T],
                                                     queue: ConsumerQueue[T])
    extends Subscription {

    def snapshot(): SubscriberSnapshot = {
      SubscriberSnapshot(requested, queue.buffered(), queue.limit())
    }


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
