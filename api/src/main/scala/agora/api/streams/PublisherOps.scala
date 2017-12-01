package agora.api.streams

import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * The PublisherOps consumes raw data of type T and groups it based on a field 'A'
  *
  * @tparam T
  */
class PublisherOps[T](val publisher: Publisher[T]) extends AnyVal {

  import PublisherOps._

  def filter(subscriber: Subscriber[T], initialRequest: Long)(predicate: T => Boolean) = {
    val keySubscriber = new FilterSubscriber[T](predicate, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  def subscribeByKey[K](subscriber: Subscriber[(K, T)], initialRequest: Long)(implicit
                                                                              selector: FieldSelector[T, K]) = {
    val keySubscriber = new KeySubscriber[T, K](selector, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  def subscribeToUpdates(subscriber: Subscriber[T], initialRequest: Long)(implicit diff: DataDiff[T, T]) = {
    val keySubscriber = new UpdateSubscriber[T](diff, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  def subscribeToDeltas[D](subscriber: Subscriber[Either[T, D]], initialRequest: Long)(implicit diff: DataDiff[T, D]) = {
    val keySubscriber = new DeltaSubscriber[T, D](diff, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }
}

object PublisherOps {
  implicit def asOps[T](publisher: Publisher[T]) = new PublisherOps[T](publisher)

  private class FilterSubscriber[T](predicate: T => Boolean, subscriber: Subscriber[T], initialRequest: Long) extends BaseSubscriber[T](initialRequest) {

    override def onError(t: Throwable) = {
      subscriber.onError(t)
    }

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = subscriber.onSubscribe(s)

    override def onNext(t: T): Unit = {
      if (predicate(t)) {
        subscriber.onNext(t)
      } else {
        request(1)
      }
    }
  }

  private class KeySubscriber[T, K](selector: FieldSelector[T, K], subscriber: Subscriber[(K, T)], initialRequest: Long)
      extends BaseSubscriber[T](initialRequest) {

    override def onError(t: Throwable) = {
      subscriber.onError(t)
    }

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = subscriber.onSubscribe(s)

    override def onNext(t: T): Unit = {
      val key = selector.select(t)
      subscriber.onNext(key -> t)
    }
  }

  private class DeltaSubscriber[T, D](diff: DataDiff[T, D], subscriber: Subscriber[Either[T, D]], initialRequest: Long)
      extends BaseSubscriber[T](initialRequest) {

    private var previous: Option[T] = None

    override def onError(t: Throwable) = subscriber.onError(t)

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = subscriber.onSubscribe(s)

    override def onNext(value: T): Unit = {
      previous match {
        case None => subscriber.onNext(Left(value))
        case Some(last) =>
          val delta = diff.diff(last, value)
          subscriber.onNext(Right(delta))
      }
      previous = Option(value)
    }
  }

  private class UpdateSubscriber[T](diff: DataDiff[T, T], subscriber: Subscriber[T], initialRequest: Long) extends BaseSubscriber[T](initialRequest) {

    private var previous: Option[T] = None

    override def onError(t: Throwable) = subscriber.onError(t)

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = subscriber.onSubscribe(s)

    override def onNext(value: T): Unit = {
      previous match {
        case None => subscriber.onNext(value)
        case Some(last) =>
          val delta = diff.diff(last, value)
          subscriber.onNext(delta)
      }
      previous = Option(value)
    }
  }

}
