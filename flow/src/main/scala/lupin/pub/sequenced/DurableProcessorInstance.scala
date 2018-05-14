package lupin.pub.sequenced

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future, Promise}

/**
  *
  * @param dao                                          the durable bit -- what's going to write down the elements received
  * @param propagateSubscriberRequestsToOurSubscription if true, requests from our subscribers will result us requesting data from our subscription
  * @param currentIndexCounter                          the id (index) counter used to mark each element
  * @tparam T
  */
class DurableProcessorInstance[T](val dao: DurableProcessorDao[T],
                                  val propagateSubscriberRequestsToOurSubscription: Boolean = true,
                                  currentIndexCounter: Long = -1L)(implicit execContext: ExecutionContext)
    extends DurableProcessor[T]
    with StrictLogging {

  type IndexSubscription = (Long, T)

  //  protected[sequenced] val dao: DurableProcessorDao[T] = args.dao
  //  val propagateSubscriberRequestsToOurSubscription = args.propagateSubscriberRequestsToOurSubscription
  private val nextIndexCounter = new AtomicLong(currentIndexCounter)

  // the DAO will likely be doing IO or some other potentially expensive operation to work out the last index
  // As it doesn't change once set, we cache it here if known.
  @volatile private var cachedLastIndex = Option.empty[Long]

  /** @return a final index if we've been signalled that we're complete
    */
  def lastIndex(): Option[Long] = {
    cachedLastIndex.orElse {
      val opt = dao.finalIndex()
      opt.foreach(_ => cachedLastIndex = opt)
      opt
    }
  }

  private[sequenced] def cancelSubscriber(subscription: DurableSubscription[T]) = {
    val propagateCancel = SubscribersLock.synchronized {
      removeSubscriber(subscription) && subscribersById.isEmpty
    }
    if (propagateCancel) {

      cancel()
    }
  }

  /** @param subscription the subscription to remove
    * @return true if the subscription was removed
    */
  private[sequenced] def removeSubscriber(subscription: DurableSubscription[T]) = {
    SubscribersLock.synchronized {
      val removed = subscribersById.contains(subscription.key)
      subscribersById = subscribersById - subscription.key
      removed
    }
  }

  def subscriberCount(): Int = SubscribersLock.synchronized(subscribersById.size)

  protected val initialIndex: Long         = nextIndexCounter.get()
  private var maxWrittenIndex              = initialIndex
  private val MaxWrittenIndexLock          = new ReentrantReadWriteLock()
  private var subscribersById              = Map[Int, DurableSubscription[T]]()
  private var numRequestedPriorToSubscribe = 0L
  private var subscriptionOpt              = Option.empty[Subscription]
  private val subscriptionPromise          = Promise[Subscription]()

  private object SubscriptionOptLock

  // keeps track of 'onError' exceptions to be used when new subscribers are added to a processor
  // which has been notified of an error (via onError)
  private var processorErrorOpt = Option.empty[Throwable]

  private val maxRequest = new MaxRequest(dao.minIndex().getOrElse(-1))

  protected def maxRequestedIndex() = maxRequest.get()

  def processorSubscription(): Option[Subscription] = subscriptionOpt

  def subscriptionFuture(): Future[Subscription] = subscriptionPromise.future

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
    * @return true if we have a subscription  and elements were requested from it
    */
  def onSubscriberRequestingUpTo(potentialNewMaxIndex: Long): Boolean = {
    requestIndex(potentialNewMaxIndex)
  }

  def request(n: Long) = {
    processorSubscription match {
      case Some(s) => s.request(n)
      case None =>
        val value = numRequestedPriorToSubscribe + n
        if (value < 0) {
          numRequestedPriorToSubscribe = Long.MaxValue
        } else {
          numRequestedPriorToSubscribe = value
        }
    }
  }

  /**
    * requests up to this index from its subscription
    *
    * @param potentialNewMaxIndex
    * @return true if this resulted in any values being requested from the upstream publisher
    */
  def requestIndex(potentialNewMaxIndex: Long): Boolean = {
    // we always track how many we want to pull, as we may be subscribed to before
    // we subscribe to an upstream publisher ourselves
    val diff = maxRequest.update(potentialNewMaxIndex)
    if (diff > 0) {
      requestFromSubscription(diff)
    } else false
  }

  protected def requestFromSubscription(n: Long) = {
    onRequest(n)
    processorSubscription.fold(false) { s =>
      s.request(n)
      true
    }
  }

  protected def newSubscriber(firstRequestedIdx: Long, subscriber: Subscriber[_ >: IndexSubscription]): DurableSubscription[T] = {
    val id = if (subscribersById.isEmpty) {
      0
    } else {
      subscribersById.keySet.max + 1
    }
    new DurableSubscription[T](id, this, firstRequestedIdx.max(-1), subscriber, execContext)
  }

  override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: IndexSubscription]): Unit = {
    val hs: DurableSubscription[T] = SubscribersLock.synchronized {
      val s = newSubscriber(index - 1, subscriber)
      require(!subscribersById.contains(s.key))
      subscribersById = subscribersById.updated(s.key, s)
      s
    }

    hs.subscriber.onSubscribe(hs)
    hs.onNewIndex(currentIndex())

    // are we in error? If so notify eagerly
    processorErrorOpt.foreach { err =>
      hs.notifyError(err)
    }

    lastIndex().foreach { idx =>
      hs.notifyComplete(idx)
    }
  }

  private def clearSubscribers() = {
    SubscribersLock.synchronized {
      subscribersById = Map.empty
    }
  }

  protected[sequenced] def currentIndex() = {
    MaxWrittenIndexLock.readLock().lock()
    try {
      maxWrittenIndex
    } finally {
      MaxWrittenIndexLock.readLock().unlock()
    }
  }

  override def latestIndex: Option[Long] = Option(currentIndex()).filterNot(_ == initialIndex)

  override def firstIndex = dao.minIndex().getOrElse(initialIndex)

  override def onNext(value: T): Unit = {
    logger.debug(s"onNext($value)")
    if (value == null) {
      throw new NullPointerException("onNext called with null")
    }

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
      logger.debug(s"Just wrote $newIndex, max index is $maxWrittenIndex")
    } finally {
      MaxWrittenIndexLock.writeLock().unlock()
    }

    foreachSubscriber(_.onNewIndex(newIndex))
  }

  private def foreachSubscriber(f: DurableSubscription[T] => Unit) = {
    subscribersById.values.foreach(f)
  }

  override def onError(t: Throwable): Unit = {
    logger.error(s"Notifying and closing on error: $t")
    if (t == null) {
      throw new NullPointerException("onError called with null")
    }
    foreachSubscriber(_.subscriber.onError(t))
    clearSubscribers()
    processorErrorOpt = processorErrorOpt.orElse(Option(t))
    SubscriptionOptLock.synchronized {
      subscriptionOpt = None
    }
  }

  def cancel(): Unit = {
    foreachSubscriber(_.cancel())
    clearSubscribers()
    val oldOpt = SubscriptionOptLock.synchronized {
      val opt = subscriptionOpt
      subscriptionOpt = None
      opt
    }
    oldOpt.foreach { s =>
      s.cancel()
    }
  }

  override def onComplete(): Unit = {
    val idx = nextIndexCounter.get()
    dao.markComplete(idx)
    val lastIdxOpt = dao.finalIndex()
    require(lastIdxOpt == Option(idx), s"dao.finalIndex() returned ${lastIdxOpt}")
    foreachSubscriber(_.notifyComplete(idx))
    clearSubscription()
  }

  protected def clearSubscription() = {
    SubscriptionOptLock.synchronized {
      subscriptionOpt = None
    }
  }

  override def onSubscribe(s: Subscription): Unit = {
    val alreadySubscribed = SubscriptionOptLock.synchronized {
      subscriptionOpt match {
        case None =>
          subscriptionOpt = Option(s)
          subscriptionPromise.trySuccess(s)
          false
        case _ => true
      }
    }

    if (alreadySubscribed) {
      s.cancel()
    } else {

      val numToRequest = if (numRequestedPriorToSubscribe > 0) {
        val num = maxRequestedIndex.max(numRequestedPriorToSubscribe)
        numRequestedPriorToSubscribe = 0
        num
      } else {
        maxRequestedIndex
      }

      // trigger any requests from our subscribers
      numToRequest match {
        case n if n > 0 => s.request(n)
        case _          =>
      }
    }
  }
}
