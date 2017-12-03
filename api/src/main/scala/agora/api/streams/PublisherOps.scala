package agora.api.streams

import cats.Semigroup
import io.circe.{Decoder, Encoder}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * The PublisherOps consumes raw data of type T and groups it based on a field 'A'
  *
  * @tparam T
  */
class PublisherOps[T](val publisher: Publisher[T]) extends AnyVal {

  import PublisherOps._

  def map[A](f: T => A): Publisher[A] = new Publisher[A] {
    override def subscribe(s: Subscriber[_ >: A]): Unit = {
      val aSubscriber = new DelegateSubscriber[T](s) {
        override def onNext(t: T) = {
          s.onNext(f(t))
        }
      }
      publisher.subscribe(aSubscriber)
    }
  }

  def onDeltas(initialRequest: Int)(onUpdate: T => Unit)(implicit enc: Encoder[T], dec: Decoder[T]) = {

    import io.circe.Json
    import io.circe.syntax._
    import org.reactivestreams.{Publisher, Subscriber}

    val jsonPublisher: Publisher[Json] = map(_.asJson)

    val fromJsonSubscriber: Subscriber[Json] = {
      val s = new BaseSubscriber[T](initialRequest) {
        override def onNext(t: T) = onUpdate(t)
      }

      s.contraMap[Json] { jsonDelta: Json =>
// TODO - we need to remember the last value to apply this to via a ConflatingQueue
        jsonDelta.as[T] match {
          case Left(e)      => throw e
          case Right(value) => value
        }
      }
    }

    val jsonOps = new PublisherOps[Json](jsonPublisher)

    implicit val jsonDelta = DataDiff.JsonDiffAsDeltas
    jsonOps.subscribeToUpdates(fromJsonSubscriber, initialRequest)
  }

  def filter(subscriber: Subscriber[T], initialRequest: Long)(predicate: T => Boolean) = {
    val keySubscriber = new FilterSubscriber[T](predicate, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  def subscribeByKey[K](subscriber: Subscriber[(K, T)], initialRequest: Long)(implicit
                                                                              selector: FieldSelector[T, K]) = {
    val keySubscriber = new KeySubscriber[T, K](selector, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  /**
    * When the delta is the same as the source type (e.g. json messages, json deltas) we have the same type
    * T which we can subscribe to and thus use T for the 'state of the world' AND the delta message
    *
    * When that's not the case, we need [[subscribeToDeltas]]
    *
    * @param subscriber
    * @param initialRequest
    * @param diff
    */
  def subscribeToUpdateDeltas(subscriber: Subscriber[T], initialRequest: Long)(implicit diff: DataDiff[T, T], isEmpty: IsEmpty[T]) = {
    val keySubscriber = new UpdateSubscriber[T](diff, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  def subscribeToUpdates(subscriber: Subscriber[T], initialRequest: Long)(implicit diff: DataDiff[T, T], isEmpty: IsEmpty[T]) = {
    val keySubscriber = new UpdateSubscriber[T](diff, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }

  /** Get notified of either a state-of-the-world Left value of T or a delta value D
    *
    * @param subscriber
    * @param initialRequest
    * @param diff
    * @tparam D
    */
  def subscribeToDeltas[D: IsEmpty](subscriber: Subscriber[Either[T, D]], initialRequest: Long)(implicit diff: DataDiff[T, D]) = {
    val keySubscriber = new DeltaSubscriber[T, D](diff, subscriber, initialRequest)
    publisher.subscribe(keySubscriber)
  }
}

object PublisherOps {

  trait LowPriorityPublisherImplicits {
    implicit def asOps[T](publisher: Publisher[T]) = new PublisherOps[T](publisher)
  }

  object implicits extends LowPriorityPublisherImplicits

  private class FilterSubscriber[T](predicate: T => Boolean, subscriber: Subscriber[T], initialRequest: Long) extends BaseSubscriber[T](initialRequest) {

    override def onError(t: Throwable) = {
      subscriber.onError(t)
    }

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = {
      subscriber.onSubscribe(s)
      super.onSubscribe(s)
    }

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

    override def onSubscribe(s: Subscription) = {
      subscriber.onSubscribe(s)
      super.onSubscribe(s)
    }

    override def onNext(t: T): Unit = {
      val key = selector.select(t)
      subscriber.onNext(key -> t)
    }
  }

  private class DeltaSubscriber[T, D: IsEmpty](diff: DataDiff[T, D], subscriber: Subscriber[Either[T, D]], initialRequest: Long)
      extends BaseSubscriber[T](initialRequest) {

    private var previous: Option[T] = None

    override def onError(t: Throwable) = subscriber.onError(t)

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = {
      subscriber.onSubscribe(s)
      super.onSubscribe(s)
    }

    override def onNext(value: T): Unit = {
      previous match {
        case None => subscriber.onNext(Left(value))
        case Some(last) =>
          val delta = diff.diff(last, value)
          import IsEmpty._
          if (delta.isEmpty) {
            request(1)
          } else {
            subscriber.onNext(Right(delta))
          }
      }
      previous = Option(value)
    }
  }

  private class UpdateSubscriber[T: IsEmpty](diff: DataDiff[T, T], subscriber: Subscriber[T], initialRequest: Long) extends BaseSubscriber[T](initialRequest) {

    private var previous: Option[T] = None

    override def onError(t: Throwable) = subscriber.onError(t)

    override def onComplete() = subscriber.onComplete()

    override def onSubscribe(s: Subscription) = {
      subscriber.onSubscribe(s)
      super.onSubscribe(s)
    }

    override def onNext(value: T): Unit = {
      previous match {
        case None => subscriber.onNext(value)
        case Some(last) =>
          val delta: T = diff.diff(last, value)

          import IsEmpty._
          if (delta.nonEmpty) {
            subscriber.onNext(delta)
          }
      }
      previous = Option(value)
      request(1)
    }
  }

}
