package lupin.sub

import org.reactivestreams.{Subscriber, Subscription}

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}

class CollectSubscriber[T](limit: Long = Long.MaxValue) extends Subscriber[T] {
  val buffer          = ListBuffer[T]()
  private val promise = Promise[List[T]]()

  def result: Future[List[T]] = promise.future

  override def onSubscribe(s: Subscription): Unit = {
    s.request(limit)
  }

  override def onNext(value: T): Unit = {
    buffer += value
  }

  override def onError(t: Throwable): Unit = {
    promise.tryFailure(t)
  }

  override def onComplete(): Unit = {
    promise.trySuccess(buffer.toList)
  }
}
