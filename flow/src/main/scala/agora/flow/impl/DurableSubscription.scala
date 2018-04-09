package agora.flow.impl

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import agora.flow.DurableProcessor.computeNumberToTake
import agora.flow.{ConsumerQueue, HasName, SubscriberSnapshot}
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.util.{Failure, Success, Try}


class DurableSubscription[T](publisher: DurableProcessorInstance[T], deadIndex: Long, initialRequestedIndex: Long, val subscriber: Subscriber[_ >: T])
  extends Subscription
    with HasName
    with StrictLogging {


  private[impl] val nextIndexToRequest = new AtomicLong(initialRequestedIndex)
  private[this] var lastRequestedIndexCounter = initialRequestedIndex

  private[this] object LastRequestedIndexCounterLock

  def lastRequestedIndex() = LastRequestedIndexCounterLock.synchronized(lastRequestedIndexCounter)

  private val totalRequested = new AtomicLong(0)
  private val totalPushed = new AtomicInteger(0)

  def name = subscriber match {
    case hn: HasName => hn.name
    case _ => toString
  }

  def snapshot(): SubscriberSnapshot = {
    val lastRequested = lastRequestedIndex()
    val next = nextIndexToRequest.get
    SubscriberSnapshot(name, totalRequested.get, totalPushed.get, lastRequested.toInt, next - lastRequested, 0, ConsumerQueue.Unbounded)
  }

  private[impl] def notifyComplete(idx: Long): Unit = {
    checkComplete(idx)
  }

  /** @param newIndex the new index available
    */
  private[impl] def onNewIndex(newIndex: Long) = {
    if (newIndex <= nextIndexToRequest.get()) {
      pull(newIndex)
    }
  }

  def complete() = {
    Try(subscriber.onComplete())
  }

  override def cancel(): Unit = {
    if (nextIndexToRequest.getAndSet(deadIndex) != deadIndex) {
      publisher.remove(this)
    }
  }

  protected def notifySubscriber(elm: T) = {
    subscriber.onNext(elm)
  }

  private def checkComplete(lastIndex: Long) = {
    if (lastIndex == lastRequestedIndexCounter) {
      logger.debug(s"$name complete at $lastIndex")
      val removed = publisher.removeSubscriber(this)
      subscriber.onComplete()
      require(removed, s"$name wasn't removed")
    } else {
      logger.trace(s"$name not complete as $lastRequestedIndexCounter != last index $lastIndex")
    }
  }

  private def pull(maxIndex: Long): Unit = {
    val idx = lastRequestedIndex()
    val nrToTake = computeNumberToTake(idx, publisher.currentIndex(), maxIndex)

    if (nrToTake > 0) {
      val lastIndex = publisher.lastIndex()

      val range = LastRequestedIndexCounterLock.synchronized {
        val fromIndex = lastRequestedIndexCounter + 1

        val toIndex = {
          val computedMax = lastRequestedIndexCounter + nrToTake
          lastIndex.fold(computedMax)(_.min(computedMax))
        }
        lastRequestedIndexCounter = toIndex
        (fromIndex to toIndex)
      }

      range.iterator.map(publisher.valueAt).foreach {
        case Success(value) =>
          notifySubscriber(value)
          totalPushed.incrementAndGet()
        case Failure(err) =>
          cancel()
          val badIndex = new Exception(s"Couldn't pull $range", err)
          logger.error(s"Cancelling on request of $range w/ $nrToTake remaining to pull", err)
          subscriber.onError(badIndex)
      }

      lastIndex.foreach(checkComplete)
    }
  }

  def publisherSubscription(): Option[Subscription] = publisher.processorSubscription()

  override def request(n: Long): Unit = request(n, publisher.propagateSubscriberRequestsToOurSubscription)

  def request(n: Long, propagateSubscriberRequest: Boolean): Unit = {
    totalRequested.addAndGet(n)

    val maxIndex = nextIndexToRequest.addAndGet(n)

    if (propagateSubscriberRequest) {
      // the child of this historic processor is pulling, so the historic processor
      // should potentially pull in turn...
      publisher.onSubscriberRequestingUpTo(this, maxIndex, n)
    }

    pull(maxIndex)
  }
}