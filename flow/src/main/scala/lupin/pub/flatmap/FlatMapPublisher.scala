package lupin.pub.flatmap

import java.util.concurrent.atomic.AtomicLong

import org.reactivestreams.{Publisher, Subscriber, Subscription}

class FlatMapPublisher[A, B](underlying : Publisher[A], pubFromA : A => Publisher[B]) extends Publisher[B] {

  override def subscribe(outerSubscriber: Subscriber[_ >: B]): Unit = {

    class WrappedSubscription(initialSubscription: Subscription) extends Subscription {
      var currentSubscription: Subscription = initialSubscription
      val requestCount = new AtomicLong(0)

      def replace(newSubscription: Subscription) = {
        currentSubscription = newSubscription
        if (requestCount.get() > 0) {
          currentSubscription.request(requestCount.get())
          requestCount.set(0)
        }
      }

      override def request(n: Long): Unit = {
        require(n > 0)
        if (requestCount.addAndGet(n) < 0) {
          requestCount.set(Long.MaxValue)
        }
        currentSubscription.request(n)
      }

      override def cancel(): Unit = currentSubscription.cancel()
    }

    object ResubscribingSubscriber extends Subscriber[A] {
      var outstandingRequested = new AtomicLong(0)

      var currentSubscription: WrappedSubscription = null

      override def onSubscribe(s: Subscription): Unit = {
        currentSubscription = new WrappedSubscription(s)
        outerSubscriber.onSubscribe(currentSubscription)
      }

      override def onNext(t: A): Unit = {
        val newPublisher: Publisher[B] = pubFromA(t)
        newPublisher.subscribe(new Subscriber[B] {
          override def onSubscribe(s: Subscription): Unit = ???

          override def onNext(t: B): Unit = ???

          override def onError(t: Throwable): Unit = ???

          override def onComplete(): Unit = ???
        })

        import lupin.implicits._
        //flatMap(newPublisher)(pubFromA)
        ???
      }

      override def onError(t: Throwable): Unit = outerSubscriber.onError(t)

      override def onComplete(): Unit = outerSubscriber.onComplete()
    }

    underlying.subscribe(ResubscribingSubscriber)
  }
}
