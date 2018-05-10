package lupin.pub.collate

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import lupin.data.HasKey
import lupin.pub.sequenced.DurableProcessor.Args
import lupin.pub.sequenced.{DurableProcessorDao, DurableProcessorInstance}
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.ExecutionContext

/**
  *
  * @param dao              the data store for writing down values at each index
  * @param propagateOnError if true, errored subscribers of this publisher will be propagated to upstream
  * @param fair             if true, then requests will try to be made evenly across all upstream publishers, even if some are currently requesting zero elements
  * @param execContext
  * @tparam K
  * @tparam T
  */
class CollatingPublisherInstance[K, T](dao: DurableProcessorDao[(K, T)],
                                       propagateOnError: Boolean,
                                       fair: Boolean)(implicit execContext: ExecutionContext)
  extends CollatingPublisher[K, T] with StrictLogging {

  // used to guard subscribersById and subscribersByIdKeys
  private object SubscribersByIdLock

  private var subscribersById = Map[K, SubscriberInst]()
  private val subscribersByIdKeys = new RotatingArray[K]() // ensures fairness in pulling from subscribers, that the first one isn't always used

  /**
    * This publisher will consume data sent from all our upstream subscriptions.
    *
    */
  private class InternalPublisher extends DurableProcessorInstance[(K, T)](Args(dao, true, -1)) {

    private object NextLock


    def outstandingRequested() = {
      val c = currentIndex()
      val m = maxRequestedIndex
      m - c
    }

    /**
      * enqueue values from subscriptions. Each subscription will be
      * pushing elements asynchronously, so we need to lock this to ensure single-threaded access to the DAO
      */
    def enqueue(key: K, value: T) = {
      NextLock.synchronized(onNext(key -> value))
    }

    def onSubscriberError(id: K, err: Throwable) = {
      val allDone = SubscribersByIdLock.synchronized {
        removeSubscriber(id)
        subscribersById.isEmpty
      }
      if (allDone || propagateOnError) {
        onError(err)
      }
    }

    def onSubscriberComplete(id: K) = {
      val allDone = SubscribersByIdLock.synchronized {
        removeSubscriber(id)
        subscribersById.isEmpty
      }
      if (allDone) {
        onComplete()
      }
    }

    def cancelSubscriber(id: K) = {
      val allDone = SubscribersByIdLock.synchronized {
        removeSubscriber(id)
        subscribersById.isEmpty
      }
      if (allDone) {
        cancel()
      }
    }

    // a single subscription used by our publisher to in-turn request from the up-stream
    // subscriptions. When subscribers for our collatingPublisherInstance request elements
    // from our 'InternalPublisher', it will then pass on those requests to its own
    // subscription -- which is this InternalPublisherSubscription
    private object InternalPublisherSubscription extends Subscription with StrictLogging {
      override def request(userRequested: Long): Unit = {


        if (subscribersById.size < 2) {
          logger.debug(s"Sending request for $userRequested to only ${subscribersById.size} subscriber")
          subscribersById.values.foreach(_.request(userRequested))
        } else {

          // we need to rotate the subscribers
          val currentlyRequestedById: Iterator[(K, Long)] = {
            subscribersByIdKeys.iterator.map(k => k -> subscribersById(k).currentlyRequested.get())
          }

          val requestUpdates: Map[K, Long] = ComputeRequested(currentlyRequestedById, userRequested, !fair)
          logger.debug(s"collating request for $userRequested elements being sent to: $requestUpdates")
          requestUpdates.foreach {
            case (id, numToRequest) => subscribersById.get(id).foreach(_.request(numToRequest))
          }
        }
      }

      override def cancel(): Unit = {
        // not used
      }
    }

    onSubscribe(InternalPublisherSubscription)
  }

  private val publisher = new InternalPublisher

  /**
    * This is the subscriber we return to give to upstream publishers.
    *
    * As those publishers may (and probably will) produce elements at different rates,
    * we should automatically request more elements when our requested elements get to zero
    * if our totally outstanding requested is > 0
    *
    * @param key
    */
  class SubscriberInst(override val key: K) extends BaseSubscriber[T] with HasKey[K] {
    // a counter to check total requested vs nr received
    val currentlyRequested = new AtomicLong(0)

    override def request(n: Long = 1) = {
      currentlyRequested.addAndGet(n)
      super.request(n)
    }

    override def onNext(t: T): Unit = {
      val remaining = currentlyRequested.decrementAndGet()
      // if we get to zero, we should automatically request more,
      // as some of the other publishers may be empty
      if (!fair && remaining == 0) {
        val outstanding = publisher.outstandingRequested()
        logger.debug(s"Fair is set, so $key requesting outstanding elms: $outstanding")
        if (outstanding > 0) {
          request(outstanding)
        }
      }
      publisher.enqueue(key, t)
    }

    override def onError(t: Throwable): Unit = publisher.onSubscriberError(key, t)

    override def onComplete(): Unit = publisher.onSubscriberComplete(key)
  }


  override def newSubscriber(key: K): SubscriberInst = {
    val sub = new SubscriberInst(key)
    SubscribersByIdLock.synchronized {
      require(!subscribersById.contains(key), s"Already subscribed to $key")
      subscribersById = subscribersById.updated(key, sub)
      subscribersByIdKeys.add(key)
    }
    sub
  }

  override def cancelSubscriber(key: K): Boolean = {
    removeSubscriber(key).fold(false) { sub =>
      sub.cancel()
      true
    }
  }

  private def removeSubscriber(key: K): Option[SubscriberInst] = {
    SubscribersByIdLock.synchronized {
      subscribersById.get(key).map { sub =>
        subscribersById = subscribersById - key
        subscribersByIdKeys.remove(key)
        sub
      }
    }
  }

  override def subscribers() = subscribersById.keySet

  override def subscribe(s: Subscriber[_ >: (K, T)]): Unit = {
    publisher.subscribe(s)
  }
}
