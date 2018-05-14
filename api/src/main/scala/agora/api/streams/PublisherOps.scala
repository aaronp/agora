package agora.api.streams

import agora.json.{JsonDiff, JsonDiffIsEmpty, JsonDiffWithValues}
import agora.core.{DataDiff, FieldSelector, IsEmpty}
import io.circe.{Decoder, Encoder, Json}
import lupin.sub.{BaseSubscriber, SubscriberDelegate}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.reflect.ClassTag

/**
  * The PublisherOps consumes raw data of type T and groups it based on a field 'A'
  *
  * @tparam T
  */
class PublisherOps[T: ClassTag](val publisher: Publisher[T]) {

  import PublisherOps._

  def map[A](f: T => A): Publisher[A] = new Publisher[A] {
    override def subscribe(s: Subscriber[_ >: A]): Unit = {
      val aSubscriber = new SubscriberDelegate[T](s) {
        override def onNext(t: T) = {
          s.onNext(f(t))
        }
      }
      publisher.subscribe(aSubscriber)
    }
  }

  def onDeltas(onUpdate: T => Unit)(implicit enc: Encoder[T], dec: Decoder[T]): DeltaSubscriber[Json, (Json, Json, JsonDiff)] = {
    import io.circe.Json
    import io.circe.syntax._
    import org.reactivestreams.{Publisher, Subscriber}

    val fromJsonSubscriber: Subscriber[Either[Json, (Json, Json, JsonDiff)]] = {
      val s = new BaseSubscriber[T] {
        override def toString = "onDeltas subscriber"

        override def onNext(t: T) = {
          logger.debug(s"onDeltas($t)")
          onUpdate(t)
          request(1)
        }
      }

      s.contraMap[Either[Json, (Json, Json, JsonDiff)]] {
        case Right((before, after, diff)) =>
          // TODO - we need to remember the last value to apply this to via a ConflatingQueue
          after.as[T] match {
            case Left(e) =>
              throw new Exception(s"Couldn't unmarshal ${implicitly[ClassTag[T]].runtimeClass.getSimpleName} from: $after", e)
            case Right(value) => value
          }
        case Left(json) =>
          json.as[T] match {
            case Left(e) =>
              throw new Exception(s"Couldn't unmarshal ${implicitly[ClassTag[T]].runtimeClass.getSimpleName} from: $json", e)
            case Right(value) => value
          }
      }
    }

    // first, publish the T as json (so we can diff the json)
    val jsonPublisher: Publisher[Json]                             = map(_.asJson)
    val jsonOps                                                    = new PublisherOps[Json](jsonPublisher)
    implicit val jsonDelta: DataDiff[Json, (Json, Json, JsonDiff)] = JsonDiffWithValues

    implicit object IsTupleEmpty extends IsEmpty[(Json, Json, JsonDiff)] {
      override def isEmpty(value: (Json, Json, JsonDiff)): Boolean = JsonDiffIsEmpty.isEmpty(value._3)
    }
    // TODO - we're getting the unmarshalled json diff, not the json for 'T'.

    // we need to get a diff which includes the full previous value
    //
    //    implicit obj

    jsonOps.subscribeToDeltas(fromJsonSubscriber)
  }

  def filter(subscriber: Subscriber[T])(predicate: T => Boolean) = {
    val keySubscriber = new FilterSubscriber[T](predicate, subscriber)
    publisher.subscribe(keySubscriber)
  }

  def subscribeByKey[K](subscriber: Subscriber[(K, T)], initialRequest: Long)(implicit
                                                                              selector: FieldSelector[T, K]) = {
    val keySubscriber = new KeySubscriber[T, K](selector, subscriber)
    publisher.subscribe(keySubscriber)
  }

  /**
    * When the delta is the same as the source type (e.g. json messages, json deltas) we have the same type
    * T which we can subscribe to and thus use T for the 'state of the world' AND the delta message
    *
    * When that's not the case, we need [[subscribeToDeltas]]
    *
    * @param subscriber
    * @param diff
    */
  def subscribeToUpdateDeltas(subscriber: Subscriber[T])(implicit diff: DataDiff[T, T], isEmpty: IsEmpty[T]): UpdateSubscriber[T] = {
    val keySubscriber = new UpdateSubscriber[T](diff, subscriber)
    publisher.subscribe(keySubscriber)
    keySubscriber
  }

  /** Get notified of either a state-of-the-world Left value of T or a delta value D
    *
    * @param subscriber
    * @param diff
    * @tparam D
    */
  def subscribeToDeltas[D: IsEmpty](subscriber: Subscriber[Either[T, D]])(implicit diff: DataDiff[T, D]) = {
    val keySubscriber = new DeltaSubscriber[T, D](diff, subscriber)
    publisher.subscribe(keySubscriber)
    keySubscriber
  }
}

object PublisherOps {

  trait LowPriorityPublisherImplicits {
    implicit def asOps[T: ClassTag](publisher: Publisher[T]) = new PublisherOps[T](publisher)
  }

  object implicits extends LowPriorityPublisherImplicits

  private class FilterSubscriber[T](predicate: T => Boolean, subscriber: Subscriber[T]) extends BaseSubscriber[T] {

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

  private class KeySubscriber[T, K](selector: FieldSelector[T, K], subscriber: Subscriber[(K, T)]) extends BaseSubscriber[T] {

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

  class DeltaSubscriber[T, D: IsEmpty](diff: DataDiff[T, D], subscriber: Subscriber[Either[T, D]]) extends BaseSubscriber[T] {

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

  class UpdateSubscriber[T: IsEmpty](diff: DataDiff[T, T], subscriber: Subscriber[T]) extends BaseSubscriber[T] {

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
