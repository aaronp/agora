package agora.flow

import java.util.concurrent.atomic.AtomicLong

import agora.flow.BasePublisher.BasePublisherSubscription
import cats.Semigroup
import org.reactivestreams.Processor

import scala.collection.JavaConverters._

/**
  * BaseProcessor an abstract, base implementation of a processor
  *
  * @tparam T the processor type
  */
trait BaseProcessor[T] extends IntKeyedPublisher[T] with BaseSubscriber[T] with Processor[T, T] {

  // the current amount requested across all subscriptions, decremented on publish, incremented on
  // 'onRequestNext'
  private val maxRequestedCounter = new AtomicLong(0L)

  def currentRequestedCount() = maxRequestedCounter.get

  /**
    * One of our subscribers just requested another to take, but we don't wanna overwhelm our subscribers.
    *
    * We calculate what to actually request here by subtracting some max or min requested across all subscriptions from
    * what we've requested for them.
    *
    * @param s
    * @param currentRequested
    * @param requested
    * @return the number to atually request
    */
  protected def calculateNumberToTake(s: BasePublisherSubscription[T], currentRequested: Long, requested: Long) = {
    val chosenToTake = calculateNumberToTakeMax(s)
    chosenToTake - currentRequested
  }

  protected def calculateNumberToTakeMax(subscriberRequestingNext: BasePublisherSubscription[T]) = {
    subscriptions().foldLeft(subscriberRequestingNext.requested) {
      case (minRequested, s) => s.requested().max(minRequested)
    }
  }

  protected def calculateNumberToTakeMin(subscriberRequestingNext: BasePublisherSubscription[T]) = {
    subscriptions().foldLeft(subscriberRequestingNext.requested) {
      case (minRequested, s) => s.requested().min(minRequested)
    }
  }

  override final def onNext(value: T) = {
    publish(value)
  }

  override def publish(value: T): Unit = {
    val counter = maxRequestedCounter.decrementAndGet()
    if (counter < 0) {
      val success = maxRequestedCounter.compareAndSet(counter, 0)
      require(success, s"Couldn't set $counter back to 0")
    }
    doOnNext(value)
  }

  /**
    * This is invoked when a subscription is notified w/ its 'onNext'
    *
    * Added as a hook to be overridden by subclasses
    *
    * @param value
    */
  protected def doOnNext(value: T) = {
    super.publish(value)
  }

  // we just need to know what the max requested is for

  /**
    * Callback function when a subscription invokes its 'request' (e.g. 'takeNext') method
    *
    * @param subscription
    * @param requested the max number requested across all subscriptions
    */
  override protected def onRequestNext(subscription: BasePublisherSubscription[T], requested: Long): Long = {

    def conditionallyRequest(tries: Int): Long = {
      require(tries >= 0, s"$this couldn't consistently update ${maxRequestedCounter}")
      val current  = maxRequestedCounter.get
      val nrToTake = calculateNumberToTake(subscription, current, requested)

      if (nrToTake > 0) {
        if (maxRequestedCounter.compareAndSet(current, current + nrToTake)) {
          request(nrToTake)
        } else {
          logger.debug(s"$this couldn't update count from $current w/ $nrToTake, retrying w/ $tries")
          conditionallyRequest(tries - 1)
        }
        nrToTake
      } else {
        logger.debug(s"$this wont't take requested '$requested' as the nr requested across all subscriptions is $nrToTake")
        0
      }
    }

    conditionallyRequest(subscriptionCount * 2)
  }
}

object BaseProcessor {

  def apply[F[_], T](newQueueArgs: F[T])(implicit asQueue: AsConsumerQueue[F]): BaseProcessor[T] with IntKeyedPublisher[T] = {
    new BaseProcessor[T] with IntKeyedPublisher[T] {
      override def newDefaultSubscriberQueue() = asQueue.newQueue(newQueueArgs)
    }
  }

  def withMaxCapacity[T](maxCapacity: Int): BaseProcessor[T] with IntKeyedPublisher[T] = {
    new BaseProcessor[T] with IntKeyedPublisher[T] {
      override def toString = s"BaseProcessor w/ ${snapshot()}"

      override def newDefaultSubscriberQueue() = ConsumerQueue.withMaxCapacity(maxCapacity)
    }
  }

  def keepingLatest[T](maxCapacity: Int): BaseProcessor[T] with IntKeyedPublisher[T] = {
    new BaseProcessor[T] with IntKeyedPublisher[T] {
      override def toString = s"BaseProcessor w/ ${snapshot()}"

      override def newDefaultSubscriberQueue() = ConsumerQueue.keepLatest(maxCapacity)
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
    new BaseProcessor[T] with IntKeyedPublisher[T] {
      override def newDefaultSubscriberQueue() = ConsumerQueue(initialValue)
    }
  }
}
