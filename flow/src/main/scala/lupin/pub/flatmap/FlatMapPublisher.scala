package lupin.pub.flatmap

import com.typesafe.scalalogging.StrictLogging
import lupin.sub.SubscriberDelegate
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * A publisher which persists a subscription across the Publishers produced by 'mapFlat'
  *
  * @param underlyingPublisher
  * @param mapFlat
  * @tparam A
  * @tparam B
  */
class FlatMapPublisher[A, B](underlyingPublisher: Publisher[A], mapFlat: A => Publisher[B]) extends Publisher[B] with StrictLogging {

  override def subscribe(originalSubscriber: Subscriber[_ >: B]): Unit = {

    val delegate = new SubscriberWrapper(originalSubscriber)
    logger.debug("subscribing to flatMapPublisher")
    underlyingPublisher.subscribe(new SubscriberDelegate[A](originalSubscriber) {
      override def onNext(value: A): Unit = {
        logger.debug(s"(outer) delegate.onNext($value), flatMapping...")
        val newPublisher = mapFlat(value)
        newPublisher.subscribe(delegate)
      }

      override def onComplete(): Unit = {
        logger.debug(s"(outer) delegate.onComplete() (onFlatMappedPublisherComplete)")
        delegate.onFlatMappedPublisherComplete()
      }

      override def onSubscribe(s: Subscription): Unit = {
        logger.debug(s"(outer) delegate.onSubscribe")
        delegate.setOriginalSubscription(s)
      }
    })
  }

  private class SubscriberWrapper(originalSubscriber: Subscriber[_ >: B]) extends Subscriber[B] {
    private var outerSubscription: Subscription = null

    // as we traverse the flatmapped publishers, we reuse this subscriber and just subscribe it to
    // the new publishers as they appear
    private var currentFlatMappedSubscription: Subscription = null
    private var currentFlatMappedSubscriptionComplete = false

    // keep track of the amount requested across the flatmapped subscriptions
    private object TotalRequestedLock

    private var totalRequested = 0L


    private var flatMappedPublisherComplete = false

    /**
      * a flatmapped publisher is done - either we're "done" done or we should see if we can request 1 more
      */
    override def onComplete(): Unit = {

      logger.debug(s"flat-mapped subscriber complete w/ $totalRequested totalRequested, " +
        s"flatMappedPublisherComplete=$flatMappedPublisherComplete, " +
        s"currentFlatMappedSubscription is $currentFlatMappedSubscription")

      //currentFlatMappedSubscription = null
      if (flatMappedPublisherComplete) {
        originalSubscriber.onComplete()
      } else {
        outerSubscription.request(1)
      }
    }

    def innerPublisherComplete() = {
      logger.debug(s"\tinnerPublisherComplete yields .. currentFlatMappedSubscriptionComplete =$currentFlatMappedSubscriptionComplete, isNull = ${currentFlatMappedSubscription == null}")
      currentFlatMappedSubscription == null || currentFlatMappedSubscriptionComplete == true

    }
    /**
      * There will be no more publishers of B produced
      */
    def onFlatMappedPublisherComplete() = {
      logger.debug(s"Inner Publisher Complete w/ $totalRequested totalRequested, " +
        s"flatMappedPublisherComplete=$flatMappedPublisherComplete, " +
        s"currentFlatMappedSubscription is $currentFlatMappedSubscription")
      // the outer, flatMapped subscription is complete, but that's not to say our current flatMapped one is
      flatMappedPublisherComplete = true

      // we may get this while still consuming a flat-mapped subscription
      if (innerPublisherComplete()) {
        originalSubscriber.onComplete()
      } else {
        logger.debug("Waiting for inner publisher to complete")
      }
    }

    private def updateTotalRequestedBy(n: Long) = {
      TotalRequestedLock.synchronized {
        val prev = totalRequested
        totalRequested = (totalRequested + n).max(0)
        if (totalRequested < 0) {
          totalRequested = Long.MaxValue
        }

        logger.debug(s"updateTotalRequestedBy($n) from $prev, totalRequested is $totalRequested")

        prev
      }
    }

    def setOriginalSubscription(subscription: Subscription): Unit = {
      require(outerSubscription == null)
      outerSubscription = subscription
      originalSubscriber.onSubscribe(new FlatmappedSubscription {
        override def requestFromOuter(n: Long): Unit = {
          logger.debug(s"subscriber.requestFromOuter($n)")
          outerSubscription.request(n)
        }

        override def request(n: Long): Unit = {
          logger.debug(s"subscriber.request($n)")
          if (n <= 0) {
            throw new IllegalArgumentException(s"$n must be > 0")
          }

          updateTotalRequestedBy(n)

          if (currentFlatMappedSubscription != null) {
            currentFlatMappedSubscription.request(n)
          } else {
            requestFromOuter(1)
          }
        }

        override def cancelOuter(): Unit = {
          logger.debug(s"subscriber.cancelOuter()")
          outerSubscription.cancel()
        }

        override def cancel(): Unit = {
          logger.debug(s"subscriber.cancel()")
          if (currentFlatMappedSubscription != null) {
            currentFlatMappedSubscription.cancel()
            currentFlatMappedSubscription = null
            currentFlatMappedSubscriptionComplete = true
          }
          cancelOuter()
        }
      })
    }

    /**
      * This is called when we've subscribed to a new Publisher of B produced from a flatMap
      *
      * @param s
      */
    override def onSubscribe(s: Subscription): Unit = {
      val n = TotalRequestedLock.synchronized(totalRequested)
      logger.debug(s"inner subscriber.onSubscribe($s) while currently requested is $n")
      currentFlatMappedSubscription = s
      currentFlatMappedSubscriptionComplete = false

      if (n > 0) {
        currentFlatMappedSubscription.request(n)
      }
    }

    override def onNext(value: B): Unit = {
      val oldRequested = updateTotalRequestedBy(-1)
      logger.debug(s"inner subscriber.onNext($value), requested is now ${oldRequested - 1}")
      originalSubscriber.onNext(value)
    }

    override def onError(t: Throwable): Unit = {
      logger.debug(s"inner subscriber.onError($t)")
      originalSubscriber.onError(t)
    }
  }

}
