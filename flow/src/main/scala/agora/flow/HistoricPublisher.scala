package agora.flow

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.ReentrantReadWriteLock

import agora.flow.ConsumerQueue.ConflatingQueue
import cats.kernel.Semigroup
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * processor which lets you subscribe to values from a particular index
  *
  * @tparam T
  */
trait HistoricProcessor[T] extends Publisher[T] with Subscriber[T] {

  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit

  override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeFrom(firstIndex, subscriber)

  def firstIndex: Long

  def latestIndex: Option[Long]
}

object HistoricProcessor extends StrictLogging {

  def conflate[T: Semigroup](initialValue: Option[T] = None, propagateSubscriberRequestsToOurSubscription: Boolean = true) = {
    new SemigroupHistoricProcessor[T](initialValue, propagateSubscriberRequestsToOurSubscription)
  }

  def apply[T]()(implicit ec: ExecutionContext): Instance[T] = apply(HistoricProcessorDao[T](), true)

  def apply[T](dao: HistoricProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true) = {
    implicit val ec = dao.executionContext
    new Instance[T](dao, propagateSubscriberRequestsToOurSubscription, new AtomicLong(-1))
  }

  private[flow] def computeNumberToTake(lastReceivedIndex: Long, latest: Long, maxIndex: Long): Long = {
    val nrToTake = {
      val maxAvailable = maxIndex.min(latest)
      val nr           = (maxAvailable - lastReceivedIndex).max(0)
      logger.trace(s"""
           |Will try to pull $nr :
           |              last received index : $lastReceivedIndex
           |  max index of published elements : $latest
           |  currently requested up to index : $maxIndex
           |                 limit to pull to : $maxAvailable
             """.stripMargin)
      nr
    }
    nrToTake
  }

  private class MaxRequest(initialValue: Long = -1L) {

    private object MaxRequestedIndexLock

    // the maximum index any of the subscribers are pulling for
    private var maxRequested = -1L

    def get() = MaxRequestedIndexLock.synchronized {
      maxRequested.max(0)
    }

    /**
      * We keep track of the greatest requested index amongst all our subscribers, and then 'request(n)' from
      * our own subscription based on the diff between the new (potentially) max index and the last one
      *
      * This simply updates the max index and returns the positive difference needed to request
      *
      * @param potentialNewMaxIndex the new max index if it is greater than the old max index
      * @return the difference from the old max or 0 if potentialNewMaxIndex is <= old max
      */
    def update(potentialNewMaxIndex: Long): Long = {
      val potentialNewMaxRequested = potentialNewMaxIndex + 1
      MaxRequestedIndexLock.synchronized {
        if (potentialNewMaxRequested > maxRequested) {
          val diff = (potentialNewMaxRequested - maxRequested.max(0)).max(0)
          maxRequested = potentialNewMaxRequested
          diff
        } else 0
      }
    }

  }

  class Instance[T](val dao: HistoricProcessorDao[T], val propagateSubscriberRequestsToOurSubscription: Boolean, nextIndexCounter: AtomicLong)
      extends HistoricProcessor[T]
      with PublisherSnapshotSupport[Int] {

    def valueAt(idx: Long): Future[T] = {
      dao.at(idx)
    }

    private implicit val ec = dao.executionContext

    def remove(value: HistoricSubscription[T]) = {

      SubscribersLock.synchronized {
        subscribers = subscribers.diff(List(this))
      }
    }

    def this(dao: HistoricProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true, currentIndexCounter: Long = -1L) = {
      this(dao, propagateSubscriberRequestsToOurSubscription, new AtomicLong(currentIndexCounter))
    }

    override def snapshot(): PublisherSnapshot[Int] = {
      val map = subscribers.zipWithIndex.map(_.swap).toMap
      PublisherSnapshot(map.mapValues(_.snapshot()))
    }

    private val initialIndex: Long  = nextIndexCounter.get()
    private var maxWrittenIndex     = initialIndex
    private val MaxWrittenIndexLock = new ReentrantReadWriteLock()
    private var subscribers         = List[HistoricSubscription[T]]()
    private var subscriptionOpt     = Option.empty[Subscription]

    private val maxRequest = new MaxRequest()

    protected def subscriberList = subscribers

    def processorSubscription(): Option[Subscription] = subscriptionOpt

    private object SubscribersLock

    /**
      * Exposes a callback hook for when we request from our subscription
      *
      * @param n the number requested
      */
    protected def onRequest(n: Long) = {}

    /**
      * Invoked by the subscriptions when they request ... we should potentially pull in turn from our
      * upstream publisher.
      *
      * @param potentialNewMaxIndex
      */
    def onSubscriberRequestingUpTo(sub: HistoricSubscription[T], potentialNewMaxIndex: Long, n: Long) = {
      // we always track how many we want to pull, as we may be subscribed to before
      // we subscribe to an upstream publisher ourselves
      val diff = maxRequest.update(potentialNewMaxIndex)
      if (diff > 0) {
        requestFromSubscription(diff)
      }
    }

    def requestFromSubscription(n: Long) = {
      subscriptionOpt.foreach { s =>
        onRequest(n)
        s.request(n)
      }
    }

    protected def newSubscriber(lastRequestedIdx: Long, subscriber: Subscriber[_ >: T]) = {
      new HistoricSubscription[T](this, initialIndex - 1, lastRequestedIdx, subscriber)
    }

    override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit = {
      val hs = SubscribersLock.synchronized {
        // we start off not having requested anything, so start 1 BEFORE the index
        val lastRequestedIndex = index - 1
        val s                  = newSubscriber(lastRequestedIndex, subscriber)
        subscribers = s :: subscribers
        s
      }
      hs.subscriber.onSubscribe(hs)
    }

    private def clearSubscribers() = {
      SubscribersLock.synchronized {
        subscribers = Nil
      }
    }

    private[flow] def currentIndex() = {
      MaxWrittenIndexLock.readLock().lock()
      try {
        maxWrittenIndex
      } finally {
        MaxWrittenIndexLock.readLock().unlock()
      }
    }

    override def latestIndex: Option[Long] = Option(currentIndex()).filterNot(_ == initialIndex)

    override val firstIndex = initialIndex + 1

    override def onNext(value: T): Unit = {
      logger.debug(s"onNext($value)")
      val newIndex    = nextIndexCounter.incrementAndGet()
      val writeFuture = dao.writeDown(newIndex, value)

      // TODO - here we could exercise a write through policy
      writeFuture.onComplete {
        case Success(_) =>
          MaxWrittenIndexLock.writeLock().lock()
          try {
            maxWrittenIndex = maxWrittenIndex.max(newIndex)
            logger.info(s"Just wrote $newIndex, max index is $maxWrittenIndex")
          } finally {
            MaxWrittenIndexLock.writeLock().unlock()
          }
          foreachSubscriber(_.onNewIndex(newIndex))
        case Failure(err) =>
          logger.error(s"Error writing $value at index $newIndex", err)
      }
    }

    private def foreachSubscriber(f: HistoricSubscription[T] => Unit) = {
      //      subscribers.size match {
      //        case 0 =>
      //        case 1 => subscribers.foreach(f)
      //        case _ => case _ => subscribers.par.foreach(f)
      //      }
      subscribers.foreach(f)
    }

    override def onError(t: Throwable): Unit = {
      logger.error(s"Notifying and closing on error: $t")
      foreachSubscriber(_.subscriber.onError(t))
      clearSubscribers()
      subscriptionOpt = None
    }

    def cancel(): Unit = {
      foreachSubscriber(_.cancel())
      clearSubscribers()
      subscriptionOpt = None
    }

    override def onComplete(): Unit = {
      foreachSubscriber(_.complete())
      clearSubscribers()
      subscriptionOpt = None
    }

    private val iWasCreatedFrom = Thread.currentThread().getStackTrace.take(10).mkString("\n\t")
    private var createdFrom     = ""

    override def onSubscribe(s: Subscription): Unit = {
      def err = {
        val msg = s"Already subscribed w/ $subscriptionOpt, can't add $s, \nI am from:$iWasCreatedFrom\nsubscribe was from $createdFrom\n\n\n"
        msg
      }

      require(subscriptionOpt.isEmpty, err)
      subscriptionOpt = Option(s)

      // trigger any requests from our subscribers
      maxRequest.get() match {
        case n if n > 0 => s.request(n)
        case _          =>
      }
    }
  }

  class HistoricSubscription[T](publisher: Instance[T], deadIndex: Long, initialRequestedIndex: Long, val subscriber: Subscriber[_ >: T])
      extends Subscription
      with HasName {
    private[flow] val nextIndexToRequest        = new AtomicLong(initialRequestedIndex)
    private[flow] val lastRequestedIndexCounter = new AtomicLong(initialRequestedIndex)

    def lastRequestedIndex() = lastRequestedIndexCounter.get()

    private val totalRequested = new AtomicLong(0)
    private val totalPushed    = new AtomicInteger(0)

    def name = subscriber match {
      case hn: HasName => hn.name
      case _           => toString
    }

    def snapshot(): SubscriberSnapshot = {
      val lastRequested = lastRequestedIndexCounter.get
      val next          = nextIndexToRequest.get
      SubscriberSnapshot(name, totalRequested.get, totalPushed.get, lastRequested.toInt, next - lastRequested, 0, ConsumerQueue.Unbounded)
    }

    def onNewIndex(newIndex: Long) = {
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

    protected def pullNext(remainingToTake: Long): Unit = {

      if (remainingToTake > 0) {
        val idx         = lastRequestedIndexCounter.incrementAndGet()
        implicit val ec = publisher.dao.executionContext

        val future = publisher.valueAt(idx)
        future.onComplete {
          case Failure(err) =>
            cancel()
            val badIndex = new Exception(s"Couldn't pull $idx", err)
            subscriber.onError(badIndex)
          case Success(elm) =>
            totalPushed.incrementAndGet()
            notifySubscriber(elm)
            pullNext(remainingToTake - 1)
        }
      }
    }

    private def pull(maxIndex: Long): Unit = {
      val idx      = lastRequestedIndexCounter.get()
      val nrToTake = computeNumberToTake(idx, publisher.currentIndex(), maxIndex)

      pullNext(nrToTake)
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

  /**
    * A conflating processor
    *
    * @param initialValue
    * @tparam T
    */
  class SemigroupHistoricProcessor[T: Semigroup](val initialValue: Option[T], val propagateSubscriberRequestsToOurSubscription: Boolean)
      extends HistoricProcessor[T]
      with PublisherSnapshotSupport[Int] {

    private object SubscriberListLock

    private var subscriptionOpt = Option.empty[Subscription]
    private var subscribers     = List[SubscriptionImpl]()
    private var pendingTakeNext = 0

    private object ProcessorCacheLock

    @volatile private var processorCache = initialValue

    def processorSubscriber() = subscriptionOpt

    override def snapshot(): PublisherSnapshot[Int] = {
      val map = subscribers.zipWithIndex.map(_.swap).toMap
      PublisherSnapshot(map.mapValues(_.snapshot()))
    }

    private val maxRequested = new MaxRequest()

    protected def onSubscriberRequesting(potentialNewMaxIndex: Long): Unit = {
      if (propagateSubscriberRequestsToOurSubscription) {
        // we always track how many we want to pull, as we may be subscribed to before
        // we subscribe to an upstream publisher ourselves
        val diff = maxRequested.update(potentialNewMaxIndex)
        if (diff > 0) {
          subscriptionOpt.foreach(_.request(diff))
        }
      }
    }

    class SubscriptionImpl(val subscriber: Subscriber[_ >: T], queue: ConflatingQueue[T], nextIndexToRequest: AtomicLong = new AtomicLong(-1))
        extends Subscription
        with HasName {
      private val totalPushed    = new AtomicInteger(0)
      private val totalRequested = new AtomicLong(0)

      override def name: String = {
        subscriber match {
          case hn: HasName => hn.name
          case _           => toString
        }
      }

      def snapshot() = {
        SubscriberSnapshot(name, totalRequested.get, totalPushed.get, totalPushed.get, queue.requested(), queue.buffered(), ConsumerQueue.Unbounded)
      }

      def onError(t: Throwable) = {
        cancel()
        subscriber.onError(t)
      }

      def complete() = {
        cancel()
        subscriber.onComplete()
      }

      override def cancel(): Unit = {
        SubscriberListLock.synchronized {
          subscribers = subscribers diff List(this)
        }
      }

      def push(elm: T) = {
        totalPushed.incrementAndGet()
        val list: List[T] = queue.offer(elm)
        list.foreach { e: T =>
          subscriber.onNext(e)
        }
      }

      override def request(n: Long): Unit = {
        totalRequested.addAndGet(n)
        onSubscriberRequesting(nextIndexToRequest.addAndGet(n))

        val elms = queue.request(n)

        elms.foreach(subscriber.onNext)
      }
    }

    override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit = {
      val queue           = ConsumerQueue[T](processorCache)
      val newSubscription = new SubscriptionImpl(subscriber, queue)
      SubscriberListLock.synchronized {
        subscribers = newSubscription :: subscribers
      }
      subscriber.onSubscribe(newSubscription)
    }

    override def firstIndex: Long = 0

    override def latestIndex: Option[Long] = None

    override def onError(t: Throwable): Unit = foreachSubscriber(_.onError(t))

    override def onComplete(): Unit = foreachSubscriber(_.complete)

    protected def notifyOnNext(value: T): T = {
      import cats.syntax.semigroup._
      val latest = ProcessorCacheLock.synchronized {
        processorCache match {
          case None =>
            processorCache = Option(value)
            value
          case Some(old) =>
            val newValue = old.combine(value)
            processorCache = Option(newValue)
            newValue
        }
      }
      foreachSubscriber(_.push(value))
      latest
    }

    override final def onNext(value: T): Unit = {
      notifyOnNext(value)
    }

    override def onSubscribe(s: Subscription): Unit = {
      require(subscriptionOpt.isEmpty)
      subscriptionOpt = Option(s)
      if (pendingTakeNext > 0) {
        s.request(pendingTakeNext)
        pendingTakeNext = 0
      }
      // trigger any requests from our subscribers
      maxRequested.get() match {
        case n if n > 0 => s.request(n)
        case _          =>
      }
    }

    @deprecated("delete me")
    def request(n: Int) = {
      subscriptionOpt match {
        case None    => pendingTakeNext = pendingTakeNext + n
        case Some(s) => s.request(n)
      }
    }

    private def foreachSubscriber(f: SubscriptionImpl => Unit) = {
      subscribers.size match {
        case 0 =>
        case 1 => subscribers.foreach(f)
        case _ => subscribers.par.foreach(f)
      }
    }

  }

}
