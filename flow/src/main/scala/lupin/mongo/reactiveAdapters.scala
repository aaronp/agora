package lupin.mongo

import java.util.concurrent.atomic.AtomicBoolean

import org.mongodb.scala.{Subscription => MongoSubscription}
import org.reactivestreams.{Publisher => ReactivePublisher, Subscriber => ReactiveSubscriber, Subscription => ReactiveSubscription}

object adapters {

  def observableAsPublisher[T](obs: org.mongodb.scala.Observable[T]): ReactivePublisher[T] = new ReactivePublisher[T] {
    override def subscribe(s: ReactiveSubscriber[_ >: T]): Unit = {
      obs.subscribe(subscriberAsObserver(s))
    }
  }

  def observerAsSubscriber[T](obs: org.mongodb.scala.Observer[T]): ReactiveSubscriber[T] = new ReactiveSubscriber[T] {
    override def onNext(t: T): Unit = obs.onNext(t)

    override def onError(t: Throwable): Unit = obs.onError(t)

    override def onComplete(): Unit = obs.onComplete()

    override def onSubscribe(s: ReactiveSubscription): Unit = {
      obs.onSubscribe(reactiveSubscriptionAsMongoSubscription(s))
    }
  }

  def subscriberAsObserver[T](obs: ReactiveSubscriber[T]): org.mongodb.scala.Observer[T] = new org.mongodb.scala.Observer[T] {

    override def onSubscribe(subscription: MongoSubscription) = {
      obs.onSubscribe(mongoSubscriptionAsSubscription(subscription))
    }

    override def onNext(result: T): Unit = {
      obs.onNext(result)
    }

    override def onError(e: Throwable): Unit = obs.onError(e)

    override def onComplete(): Unit = {
      obs.onComplete()
    }
  }

  def mongoSubscriptionAsSubscription(mongoSubscription: MongoSubscription): ReactiveSubscription = {
    new ReactiveSubscription {
      override def request(n: Long): Unit = mongoSubscription.request(n)

      override def cancel(): Unit = mongoSubscription.unsubscribe()
    }
  }

  def reactiveSubscriptionAsMongoSubscription(reactiveSubscription: ReactiveSubscription): MongoSubscription = {
    new MongoSubscription {
      private val unsubscribed = new AtomicBoolean(false)

      override def request(n: Long): Unit = reactiveSubscription.request(n)

      override def unsubscribe(): Unit = if (unsubscribed.compareAndSet(false, true)) {
        reactiveSubscription.cancel()
      }

      override def isUnsubscribed(): Boolean = unsubscribed.get()
    }
  }
}
