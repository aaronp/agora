package lupin.sub

import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.{Future, Promise}

class ForeachSubscriber[T](f: T => Unit) extends Subscriber[T] {
  private val promise = Promise[Boolean]()

  def result: Future[Boolean] = promise.future

  override def onSubscribe(s: Subscription): Unit = {
    s.request(Long.MaxValue)
  }

  override def onNext(value: T): Unit = {
    f(value)
  }

  override def onError(t: Throwable): Unit = {
    promise.tryFailure(t)
  }

  override def onComplete(): Unit = {
    promise.trySuccess(true)
  }
}

