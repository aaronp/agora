package agora.flow.impl

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

import agora.flow.DurableProcessor.Args
import agora.flow.{DurableProcessor, DurableProcessorDao, PublisherSnapshot, PublisherSnapshotSupport}
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}



/** @param args
  * @tparam T
  */
class DurableProcessorInstance[T](args: Args[T]) extends DurableProcessor[T] with PublisherSnapshotSupport[Int] with StrictLogging {

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
  private var processorErrorOpt = Option.empty[Throwable]

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

    processorErrorOpt.foreach { err =>
      hs.subscriber.onError(err)
      SubscribersLock.synchronized {
        subscribers = subscribers.diff(List(hs))
      }
    }
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
    processorErrorOpt = processorErrorOpt.orElse(Option(t))
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