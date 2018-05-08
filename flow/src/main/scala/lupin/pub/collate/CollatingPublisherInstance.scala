package lupin.pub.collate

import java.util.concurrent.atomic.AtomicLong

import com.typesafe.scalalogging.StrictLogging
import lupin.pub.impl.HasKey
import lupin.pub.sequenced.DurableProcessor.Args
import lupin.pub.sequenced.{DurableProcessorDao, DurableProcessorInstance}
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.ExecutionContext


class CollatingPublisherInstance[K, T](dao: DurableProcessorDao[(K,T)], propagateOnError: Boolean)(implicit execContext: ExecutionContext) extends CollatingPublisher[K, T] {

  // used to guard subscribersById and subscribersByIdKeys
  private object SubscribersByIdLock
  private var subscribersById = Map[K, Sub]()
  private val subscribersByIdKeys = new RotatingArray[K]() // ensures fairness in pulling from subscribers, that the first one isn't always used

  private class InternalPublisher extends DurableProcessorInstance[(K, T)](Args(dao, true, -1)) {

    private object NextLock

    /**
      * enqueue values from subscriptions. Each subscription will be
      * pushing elements asynchronously, so we need to lock this to ensure single-threaded access to the DAO
      *
      */
    def enqueue(key : K, value: T) = NextLock.synchronized(onNext(key -> value))

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
  }

  private val publisher = new InternalPublisher

  private class Sub(override val key: K) extends BaseSubscriber[T] with HasKey[K] {
    // a counter to check total requested vs nr received
    val currentlyRequested = new AtomicLong(0)

    override def request(n: Long = 1) = {
      currentlyRequested.addAndGet(n)
      super.request(n)
    }

    override def onNext(t: T): Unit = {
      currentlyRequested.decrementAndGet()
      publisher.enqueue(key, t)
    }

    override def onError(t: Throwable): Unit = publisher.onSubscriberError(key, t)

    override def onComplete(): Unit = publisher.onSubscriberComplete(key)
  }

  private object Driver extends Subscription with StrictLogging {
    override def request(n: Long): Unit = {
      if (subscribersById.size < 2) {
        subscribersById.values.foreach(_.request(n))
      } else {

        // we need to rotate the subscribers
        val currentlyRequestedById = {
          subscribersByIdKeys.iterator.map(k => k -> subscribersById(k).currentlyRequested.get())
        }
        val requestUpdates = ComputeRequested(currentlyRequestedById, n)
        logger.debug(s"request for $n results in $requestUpdates")
        requestUpdates.foreach {
          case (id, numToRequest) => subscribersById.get(id).foreach(_.request(numToRequest))
        }
      }
    }

    override def cancel(): Unit = {
      // not used
    }
  }
  publisher.onSubscribe(Driver)

  override def newSubscriber(key: K): Subscriber[T] with HasKey[K] = {
    val sub = new Sub(key)
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

  private def removeSubscriber(key: K): Option[Sub] = {
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
