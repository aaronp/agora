package lupin.sub

import org.reactivestreams.Subscription

import scala.concurrent.{Future, Promise}

class HeadSubscriber[T]() extends BaseSubscriber[T] {
  private val promise = Promise[T]()

  def result: Future[T] = promise.future

  override def onSubscribe(s: Subscription): Unit = {
    super.onSubscribe(s)
    s.request(1)
  }

  override def onNext(value: T): Unit = {
    promise.trySuccess(value)
    subscriptionOption.foreach(_.cancel())
  }

  override def onError(t: Throwable): Unit = {
    promise.tryFailure(t)
  }

  override def onComplete(): Unit = {
    promise.tryFailure(new Exception("No data received"))
  }
}
