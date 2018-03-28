package agora.flow

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.util.concurrent.locks.ReentrantReadWriteLock

import agora.flow.ConsumerQueue.ConflatingQueue
import cats.kernel.Semigroup
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * processor which lets you subscribe to values from a particular index
  *
  * @tparam T
  */
trait DurableProcessor[T] extends Publisher[T] with Subscriber[T] {

  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit

  override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeFrom(firstIndex, subscriber)

  def firstIndex: Long

  def latestIndex: Option[Long]
}

object DurableProcessor extends StrictLogging {

  def conflate[T: Semigroup](initialValue: Option[T] = None, propagateSubscriberRequestsToOurSubscription: Boolean = true) = {
    new SemigroupDurableProcessor[T](initialValue, propagateSubscriberRequestsToOurSubscription)
  }

  def apply[T]()(implicit ec: ExecutionContext): Instance[T] = apply(DurableProcessorDao[T](), true)

  def apply[T](dao: DurableProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true) = {
    new Instance[T](Args(dao, propagateSubscriberRequestsToOurSubscription, -1))
  }

  /**
    *
    * @param dao                                          the durable bit -- what's going to write down the elements received
    * @param propagateSubscriberRequestsToOurSubscription if true, requests from our subscribers will result us requesting data from our subscription
    * @param nextIndex the id (index) counter used to mark each element
    * @tparam T
    */
  case class Args[T](dao: DurableProcessorDao[T],
                  propagateSubscriberRequestsToOurSubscription: Boolean = true,
                  nextIndex : Long = -1)

  private[flow] def computeNumberToTake(lastReceivedIndex: Long, latest: Long, maxIndex: Long): Long = {
    val nrToTake = {
      val maxAvailable = maxIndex.min(latest)
      val nr = (maxAvailable - lastReceivedIndex).max(0)
      logger.trace(
        s"""
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

  /** @param args
    * @tparam T
    */
  class Instance[T](args : Args[T])
    extends DurableProcessor[T]
      with PublisherSnapshotSupport[Int] {

    val dao = args.dao
    val propagateSubscriberRequestsToOurSubscription = args.propagateSubscriberRequestsToOurSubscription
    private val nextIndexCounter = new AtomicLong(args.nextIndex)

    def valueAt(idx: Long) = dao.at(idx)

    def remove(value: DurableSubscription[T]) = {

      SubscribersLock.synchronized {
        subscribers = subscribers.diff(List(this))
      }
    }

    def this(dao: DurableProcessorDao[T], propagateSubscriberRequestsToOurSubscription: Boolean = true, currentIndexCounter: Long = -1L) = {
      this(Args(dao, propagateSubscriberRequestsToOurSubscription, currentIndexCounter))
    }

    override def snapshot(): PublisherSnapshot[Int] = {
      val map = subscribers.zipWithIndex.map(_.swap).toMap
      PublisherSnapshot(map.mapValues(_.snapshot()))
    }

    private val initialIndex: Long = nextIndexCounter.get()
    private var maxWrittenIndex = initialIndex
    private val MaxWrittenIndexLock = new ReentrantReadWriteLock()
    private var subscribers = List[DurableSubscription[T]]()
    private var subscriptionOpt = Option.empty[Subscription]

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
    def onSubscriberRequestingUpTo(sub: DurableSubscription[T], potentialNewMaxIndex: Long, n: Long) = {
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
      new DurableSubscription[T](this, initialIndex - 1, lastRequestedIdx, subscriber)
    }

    override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit = {
      val hs = SubscribersLock.synchronized {
        // we start off not having requested anything, so start 1 BEFORE the index
        val lastRequestedIndex = index - 1
        val s = newSubscriber(lastRequestedIndex, subscriber)
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
      val newIndex: Long = nextIndexCounter.incrementAndGet()

      // TODO - here we could exercise a write through policy
      // we would have to make 'writeDown' return a future, and somehow guarantee
      // that the writes completed in order so as to notify our subscribers in order.
      // Also to consider is that 'onNext' is where the back-pressure magic typically happens.
      // if we just fire off to an IO thread, we'd have to make sure our 'onNext' handling was still
      // adequately honoring the right back-pressure and not overwhelming our subscribers
      dao.writeDown(newIndex, value)

      MaxWrittenIndexLock.writeLock().lock()
      try {
        maxWrittenIndex = maxWrittenIndex.max(newIndex)
        logger.info(s"Just wrote $newIndex, max index is $maxWrittenIndex")
      } finally {
        MaxWrittenIndexLock.writeLock().unlock()
      }

      foreachSubscriber(_.onNewIndex(newIndex))
    }

    private def foreachSubscriber(f: DurableSubscription[T] => Unit) = {
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
    private var createdFrom = ""

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
        case _ =>
      }
    }
  }

  class DurableSubscription[T](publisher: Instance[T], deadIndex: Long, initialRequestedIndex: Long, val subscriber: Subscriber[_ >: T])
    extends Subscription
      with HasName {
    private[flow] val nextIndexToRequest = new AtomicLong(initialRequestedIndex)
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

    private def pull(maxIndex: Long): Unit = {
      val idx = lastRequestedIndex()
      val nrToTake = computeNumberToTake(idx, publisher.currentIndex(), maxIndex)

      if (nrToTake > 0) {
        val range = LastRequestedIndexCounterLock.synchronized {
          val fromIndex = lastRequestedIndexCounter + 1
          val toIndex = lastRequestedIndexCounter + nrToTake
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

  /**
    * A conflating processor
    *
    * @param initialValue
    * @tparam T
    */
  class SemigroupDurableProcessor[T: Semigroup](val initialValue: Option[T], val propagateSubscriberRequestsToOurSubscription: Boolean)
    extends DurableProcessor[T]
      with PublisherSnapshotSupport[Int] {

    private object SubscriberListLock

    private var subscriptionOpt = Option.empty[Subscription]
    private var subscribers = List[SubscriptionImpl]()
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
      private val totalPushed = new AtomicInteger(0)
      private val totalRequested = new AtomicLong(0)

      override def name: String = {
        subscriber match {
          case hn: HasName => hn.name
          case _ => toString
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
      val queue = ConsumerQueue[T](processorCache)
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
        case _ =>
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
