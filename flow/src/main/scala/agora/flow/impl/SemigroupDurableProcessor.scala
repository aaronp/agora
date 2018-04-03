package agora.flow.impl

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import agora.flow.ConsumerQueue.ConflatingQueue
import agora.flow._
import cats.kernel.Semigroup
import org.reactivestreams.{Subscriber, Subscription}


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
  private var processorErrorOpt = Option.empty[Throwable]

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
      processorErrorOpt = processorErrorOpt.orElse(Option(t))
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

    processorErrorOpt.foreach { err =>
      subscriber.onError(err)
      SubscriberListLock.synchronized {
        subscribers = subscribers.diff(List(newSubscription))
      }
    }
  }

  override def firstIndex: Long = 0

  override def latestIndex: Option[Long] = None

  override def onError(t: Throwable): Unit = {
    foreachSubscriber(_.onError(t))
    processorErrorOpt = processorErrorOpt.orElse(Option(t))
  }

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