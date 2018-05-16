package lupin.pub.flatmap

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
class FlatMapPublisher[A, B](underlyingPublisher: Publisher[A], mapFlat: A => Publisher[B]) extends Publisher[B] {

  override def subscribe(originalSubscriber: Subscriber[_ >: B]): Unit = {

    val delegate = new SubscriberWrapper(originalSubscriber)
    underlyingPublisher.subscribe(new SubscriberDelegate[A](originalSubscriber) {
      override def onNext(value: A): Unit = {
        val newPublisher = mapFlat(value)
        newPublisher.subscribe(delegate)
      }

      override def onComplete(): Unit = {
        delegate.onFlatMappedPublisherComplete()
      }

      override def onSubscribe(s: Subscription): Unit = {
        delegate.setOriginalSubscription(s)
      }
    })
  }

  private class SubscriberWrapper(originalSubscriber: Subscriber[_ >: B]) extends Subscriber[B] {
    private var outerSubscription: Subscription = null

    // as we traverse the flatmapped publishers, we reuse this subscriber and just subscribe it to
    // the new publishers as they appear
    private var currentFlatmappedSubscription: Subscription = null

    // keep track of the amount requested across the flatmapped subscriptions
    private object TotalRequestedLock

    private var totalRequested = 0L


    private var flatMappedPublisherComplete = false


    /**
      * a flatmapped publisher is done - either we're "done" done or we should see if we can request 1 more
      */
    override def onComplete(): Unit = {
      currentFlatmappedSubscription = null
      if (flatMappedPublisherComplete) {
        originalSubscriber.onComplete()
      } else {
        val prev = updateTotalRequestedBy(-1)
        if (prev > 0) {
          outerSubscription.request(1)
        }
      }
    }

    /**
      * There will be no more publishers of B produced
      */
    def onFlatMappedPublisherComplete() = {
      // the outer, flatMapped subscription is complete, but that's not to say our current flatMapped one is
      flatMappedPublisherComplete = true
      if (currentFlatmappedSubscription == null) {
        originalSubscriber.onComplete()
      }
    }

    private def updateTotalRequestedBy(n: Long) = {
      TotalRequestedLock.synchronized {
        val prev = totalRequested
        totalRequested = (totalRequested + n).max(0)
        if (totalRequested < 0) {
          totalRequested = Long.MaxValue
        }
        prev
      }
    }

    def setOriginalSubscription(subscription: Subscription): Unit = {
      require(outerSubscription == null)
      outerSubscription = subscription
      originalSubscriber.onSubscribe(new FlatmappedSubscription {
        override def requestFromOuter(n: Long): Unit = {
          outerSubscription.request(n)
        }

        override def request(n: Long): Unit = {
          require(n > 0)

          if (currentFlatmappedSubscription != null) {
            updateTotalRequestedBy(n)
            currentFlatmappedSubscription.request(n)
          } else {
            updateTotalRequestedBy(n - 1)
            requestFromOuter(1)
          }
        }

        override def cancelOuter(): Unit = {
          outerSubscription.cancel()
        }

        override def cancel(): Unit = {
          if (currentFlatmappedSubscription != null) {
            currentFlatmappedSubscription.cancel()
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
      currentFlatmappedSubscription = s

      val n = updateTotalRequestedBy(-1)
      if (n > 1) {
        currentFlatmappedSubscription.request(n - 1)
      }
    }

    override def onNext(value: B): Unit = {
      updateTotalRequestedBy(-1)
      originalSubscriber.onNext(value)
    }

    override def onError(t: Throwable): Unit = originalSubscriber.onError(t)
  }

}
