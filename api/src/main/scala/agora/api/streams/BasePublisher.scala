package agora.api.streams

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Queue => jQueue}

import agora.api.streams.BasePublisher.BasePublisherSubscription
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.util.control.NonFatal

/**
  * A publishing skeleton which exposes a 'publish' to push elements to subscribers
  *
  * @param maxCapacity
  * @tparam T
  */
class BasePublisher[T](maxCapacity: Int) extends Publisher[T] with StrictLogging {

  private val ids               = new AtomicInteger(0)
  private val subscriptionsById = new ConcurrentHashMap[Int, BasePublisherSubscription[T]]()

  def remove(id: Int): Unit = {
    subscriptionsById.remove(id)
  }

  protected def newSubscription(s: Subscriber[_ >: T]): BasePublisherSubscription[T] = {
    val id    = ids.incrementAndGet()
    val queue = new java.util.concurrent.LinkedBlockingQueue[T](maxCapacity)
    new BasePublisher.BasePublisherSubscription[T](id, this, s, queue)
  }

  override def subscribe(s: Subscriber[_ >: T]) = {
    val subscription = newSubscription(s)
    subscriptionsById.put(subscription.id, subscription)
    s.onSubscribe(subscription)
  }

  /**
    * Push an element onto the queue
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

  private[streams] class BasePublisherSubscription[T](val id: Int, val publisher: BasePublisher[T], val subscriber: Subscriber[_ >: T], queue: jQueue[T])
      extends Subscription {

    private object Lock

    @volatile private var requested = 0L

    override def cancel(): Unit = publisher.remove(id)

    override def request(n: Long): Unit = Lock.synchronized {
      requested = drain(requested + n)
    }

    def onElement(event: T) = {
      try {
        Lock.synchronized {
          queue.add(event)
        }
      } catch {
        case NonFatal(e) =>
          try {
            logger.debug(s"Notifying subscriber of: $e")
            subscriber.onError(e)
          } catch {
            case NonFatal(subscriberError) =>
              logger.warn(s"Subscriber (re) threw error $e on event $event : $subscriberError")
          }
          cancel()
      }
      if (requested > 0) {
        requested = drain(requested)
      }
    }

    private def drain(totalRequested: Long): Long = {
      var i = totalRequested

      def retVal = {
        logger.debug(s"Drained ${totalRequested - i} of $totalRequested events")
        i
      }

      Lock.synchronized {
        while (i > 0) {
          val next: T = queue.poll()
          if (next != null) {
            i = i - 1
            try {
              subscriber.onNext(next)
            } catch {
              case NonFatal(e) =>
                logger.warn(s"subscriber.onNext($next) threw $e")
            }
          } else {
            return retVal
          }
        }
      }
      return retVal
    }
  }

}
