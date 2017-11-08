package agora.api.exchange.observer

import java.util.{Queue => jQueue}

import agora.api.exchange.QueueStateResponse
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.util.control.NonFatal

/**
  * An [[ExchangeObserver]] which publishes events to other subscriptions.
  *
  * We can construct an [[agora.api.exchange.Exchange]] w/ one of these puppies,
  * then allow other subscribers to come and go from here.
  *
  * @param initialStateOfTheWorld the state of the world when this publisher was created
  * @param maxCapacity            the max capacity of the subscription queues
  */
class SingleSubscriptionExchangePublisher private (maxCapacity: Int, var initialStateOfTheWorld: QueueStateResponse)
    extends ExchangeObserver
    with Publisher[ExchangeNotificationMessage]
    with StrictLogging {

  import SingleSubscriptionExchangePublisher._

  private var subscriptionOpt = Option.empty[ExchangeObserverSubscription]

  private def remove(): Boolean = {
    logger.debug(s"Removing subscription")
    val removed = subscriptionOpt.isDefined
    subscriptionOpt = None
    removed
  }

  def subscription = subscriptionOpt

  override def subscribe(subscriber: Subscriber[_ >: ExchangeNotificationMessage]) = {
    logger.debug(s"Creating subscription with $initialStateOfTheWorld")
    val queue        = new java.util.concurrent.LinkedBlockingQueue[ExchangeNotificationMessage](maxCapacity)
    val subscription = new ExchangeObserverSubscription(queue, this, subscriber)
    require(subscriptionOpt.isEmpty, "single-use publisher already already subscribed to")
    subscriptionOpt = Option(subscription)
    subscriber.onSubscribe(subscription)

    // we're a little naughty and send a message before an initial request
    subscription.onEvent(OnStateOfTheWorld(agora.api.time.now(), initialStateOfTheWorld))

    // let's not hold on to this data forever and always ... it was just needed here when the initial subscription was made
    initialStateOfTheWorld = null
  }

  override def onEvent(event: ExchangeNotificationMessage): Unit = {
    logger.debug(s"Notifying ${subscriptionOpt.size} subscriber(s) of $event")
    subscriptionOpt.foreach(_.onEvent(event))
  }
}

object SingleSubscriptionExchangePublisher {

  def apply(maxCapacity: Int = Int.MaxValue, stateOfTheWorld: QueueStateResponse = QueueStateResponse(Nil, Nil)): SingleSubscriptionExchangePublisher = {
    new SingleSubscriptionExchangePublisher(maxCapacity, stateOfTheWorld)
  }

  /**
    * A reactive-streams Subscription which will notify the subscriber on each event
    */
  case class ExchangeObserverSubscription(queue: jQueue[ExchangeNotificationMessage],
                                          publisher: SingleSubscriptionExchangePublisher,
                                          subscriber: Subscriber[_ >: ExchangeNotificationMessage])
      extends Subscription
      with StrictLogging {

    /**
      * We keep a running total of the number of items requested, and then 'drain' the queue
      * every time an event arrives
      */
    @volatile private var requested: Long = 0

    private object Lock

    def onEvent(event: ExchangeNotificationMessage) = {
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
        drain(requested)
      }
    }

    override def cancel(): Unit = publisher.remove()

    override def request(n: Long): Unit = {
      requested = drain(requested + n)
    }

    private def drain(totalRequested: Long): Long = {
      var i = totalRequested

      def retVal = {
        logger.debug(s"Drained ${totalRequested - i} of $totalRequested events")
        i
      }

      Lock.synchronized {
        while (i > 0) {
          val next: ExchangeNotificationMessage = queue.poll()
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
