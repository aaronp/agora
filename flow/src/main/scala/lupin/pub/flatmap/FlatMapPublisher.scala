package lupin.pub.flatmap

import lupin.sub.SubscriberDelegate
import org.reactivestreams.{Publisher, Subscriber, Subscription}


class FlatMapPublisher[A, B](underlyingPublisher: Publisher[A], mapFlat: A => Publisher[B]) extends Publisher[B] {

  override def subscribe(originalSubscriber: Subscriber[_ >: B]): Unit = {

    val delegate = new SubscriberWrapper(originalSubscriber)
    underlyingPublisher.subscribe(new SubscriberDelegate[A](originalSubscriber) {
      override def onNext(value: A): Unit = {
        val newPublisher = mapFlat(value)
        newPublisher.subscribe(delegate)
      }

      override def onComplete(): Unit = {
        delegate.onCompleteInner()
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

    def onCompleteInner() = {
      currentFlatmappedSubscription = null
      if (flatMappedPublisherComplete) {
        originalSubscriber.onComplete()
      } else {
        val n = TotalRequestedLock.synchronized(totalRequested)
        if (n > 0) {
          outerSubscription.request(n)
        }
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
          TotalRequestedLock.synchronized {
            totalRequested = totalRequested + n
            if (totalRequested < 0) {
              totalRequested = Long.MaxValue
            }
          }
          if (currentFlatmappedSubscription != null) {
            currentFlatmappedSubscription.request(n)
          } else {
            requestFromOuter(n)
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

    override def onSubscribe(s: Subscription): Unit = {
      currentFlatmappedSubscription = s
      val n = TotalRequestedLock.synchronized(totalRequested)
      if (n > 0) {
        currentFlatmappedSubscription.request(n)
      }
    }

    override def onNext(value: B): Unit = {
      TotalRequestedLock.synchronized {
        totalRequested = (totalRequested - 1).max(0)
      }
      originalSubscriber.onNext(value)
    }

    override def onError(t: Throwable): Unit = originalSubscriber.onError(t)

    override def onComplete(): Unit = {
      // the outer, flatMapped subscription is complete, but that's not to say our current flatMapped one is
      flatMappedPublisherComplete = true
      if (currentFlatmappedSubscription == null) {
        originalSubscriber.onComplete()
      }
    }
  }

}
