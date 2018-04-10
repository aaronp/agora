package agora.flow.impl

import java.util.concurrent.Exchanger
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import agora.flow.DurableProcessor.computeNumberToTake
import agora.flow.{ConsumerQueue, DurableProcessorDao, HasName, SubscriberSnapshot}
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.collection.immutable.NumericRange
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  *
  * @param inputPublisher
  * @param initialRequestedIndex
  * @param subscriber
  * @tparam T
  */
class DurableSubscription[T](inputPublisher: DurableProcessorInstance[T],
                             initialRequestedIndex: Long,
                             val subscriber: Subscriber[_ >: T],
                             execContext: ExecutionContext)
  extends Subscription
    with HasName
    with StrictLogging {


  // this gets incremented every time 'request(n)' is called to track the subscription's
  // intended max index. The 'lastRequestedIndexCounter' is similar,
  private[flow] val nextIndexToRequest = new AtomicLong(initialRequestedIndex)

  // the last index requested from the publisher, used to compute which indices should be asked for when 'request' is called
  private[this] var lastRequestedIndexCounter = initialRequestedIndex

  private[this] object LastRequestedIndexCounterLock

  // the publisher of the data, kept in an option which is set to None once we're cancelled/errored/completed
  private[this] var publisherOpt = Option(inputPublisher)

  // kept for snapshots
  private val totalRequested = new AtomicLong(0)
  private val totalPushed = new AtomicInteger(0)
  
  private var rangeBuffer = DurableSubscription.emptyBuffer()
  private lazy val pullTask: DurableSubscription.PullRunnable[T] = {
    val task = DurableSubscription.PullRunnable(inputPublisher.dao)
    execContext.execute(task)
    task
  }

  def lastRequestedIndex() = LastRequestedIndexCounterLock.synchronized(lastRequestedIndexCounter)

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

  private[impl] def notifyError(err: Throwable): Unit = {
    subscriber.onError(err)
    remove()
  }

  /** @param newIndex the new index available
    */
  private[impl] def onNewIndex(newIndex: Long) = {
    if (newIndex <= nextIndexToRequest.get()) {
      pull(newIndex)
    }
  }

  private def remove() = {
    publisherOpt.fold(false) { publisher =>
      publisherOpt = None
      publisher.removeSubscriber(this)
    }
  }

  override def cancel(): Unit = {
    if (nextIndexToRequest.getAndSet(-1) != -1) {
      remove()
    }
  }

  protected def notifySubscriber(elm: T) = {
    subscriber.onNext(elm)
  }

  private def checkComplete(lastIndex: Long) = {
    if (lastIndex == lastRequestedIndexCounter) {
      logger.debug(s"$name complete at $lastIndex")
      val removed = remove()
      if (removed) {
        subscriber.onComplete()
      } else {
        logger.error(s"$name couldn't be removed")
      }
    } else {
      logger.trace(s"$name not complete as $lastRequestedIndexCounter != last index $lastIndex")
    }
  }

  private def pullRange(indices: NumericRange.Inclusive[Long]) = {
    pullTask.ranges.exchange(Option(rangeBuffer))

  }

  private def pull(maxIndex: Long): Unit = {
    publisherOpt.foreach { publisher: DurableProcessorInstance[T] =>

      val idx = lastRequestedIndex()
      val nrToTake = computeNumberToTake(idx, publisher.currentIndex(), maxIndex)

      if (nrToTake > 0) {
        val lastIndex = publisher.lastIndex()

        val range: NumericRange.Inclusive[Long] = LastRequestedIndexCounterLock.synchronized {
          val fromIndex = lastRequestedIndexCounter + 1

          val toIndex = {
            val computedMax = lastRequestedIndexCounter + nrToTake
            lastIndex.fold(computedMax)(_.min(computedMax))
          }
          lastRequestedIndexCounter = toIndex
          (fromIndex to toIndex)
        }

        pullRange(range)
        //        lastIndex.foreach(checkComplete)
      }
    }
  }

  def publisherSubscription(): Option[Subscription] = publisherOpt.flatMap(_.processorSubscription)

  override def request(n: Long): Unit = {
    if (n <= 0) {
      val err = new IllegalArgumentException(s"Invalid request for $n elements. According to the reactive stream spec #309 only positive values may be requested")
      notifyError(err)
    } else {
      publisherOpt.foreach { publisher =>
        doRequest(n, publisher, publisher.propagateSubscriberRequestsToOurSubscription)
      }
    }
  }

  def request(n: Long, propagateSubscriberRequest: Boolean): Unit = {
    publisherOpt.foreach { publisher =>
      doRequest(n, publisher, propagateSubscriberRequest)
    }
  }

  private def doRequest(n: Long, publisher: DurableProcessorInstance[T], propagateSubscriberRequest: Boolean): Unit = {
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

object DurableSubscription {
  type Buffer = ListBuffer[NumericRange.Inclusive[Long]]

  def emptyBuffer(): Buffer = ListBuffer[NumericRange.Inclusive[Long]]()

  private case class PullRunnable[T](dao: DurableProcessorDao[T]) extends Runnable with StrictLogging {

    val ranges: Exchanger[Option[Buffer]] = new Exchanger[Option[Buffer]]

    def drain(buffer: Buffer) = {

    }

    override def run(): Unit = {
      var bufferOpt = Option(emptyBuffer())
      while (bufferOpt.isDefined) {
        bufferOpt.foreach { buffer =>
          drain(buffer)
          bufferOpt = ranges.exchange(bufferOpt)
          logger.debug(s"got new buffer $bufferOpt")
        }
      }
      logger.debug("pull job completing")
    }
  }

}