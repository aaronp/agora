package lupin.pub

import cats.{FlatMap, Functor}
import lupin.pub.flatmap.PublisherFlatMap
import lupin.{Publishers, Subscribers}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{ExecutionContext, Future}

/**
  * This keeps the typeclass instances separate from the conversions which require them, just to hopefully
  * make things easier to provide alternative impls (by excluding this trait to take things out of scope)
  * while still bringing in the conversions
  */
trait LowPriorityInstances {
  implicit def asFlatMap: FlatMap[Publisher] = PublisherFlatMap
}

trait LowPriorityPublisherConversions {

  /**
    * Publisher is invariant in T, so we need to expose an explicit conversion to ensure mapping/flatMapping
    * specific subclasses of Publisher 'P' still return the same type and not just a generic Publisher[T]
    *
    * @param pub the subclassed publisher
    * @tparam T the parameter type for the Publisher subclass
    * @tparam P the publisher subclass type
    * @return a RichPublisher
    */
  implicit def asRichPublisher[T, P[_] <: Publisher[_]](pub: P[T]): PublisherImplicits.RichPublisher[T, P] = new PublisherImplicits.RichPublisher[T, P](pub)

  implicit def asRichPublisher2[T](pub: Publisher[T]): PublisherImplicits.RichPublisher[T, Publisher] = {
    asRichPublisher(pub)
  }

}

trait LowPriorityPublisherImplicits extends LowPriorityPublisherConversions with LowPriorityInstances

object PublisherImplicits extends LowPriorityPublisherImplicits with LowPriorityInstances {

  class RichPublisher[T, P[_] <: Publisher[_]](val underlying: P[T]) extends AnyVal {

    private def publisher = underlying.asInstanceOf[Publisher[T]]

    def subscribeWith[A >: T, S <: Subscriber[A]](s: S): S = {
      publisher.subscribe(s)
      s
    }

    def zipWithIndex: Publisher[(T, Long)] = {
      foldWith[Long, (T, Long)](0L) {
        case entry @ (lastIdx, _) =>
          val nextIndex = lastIdx + 1
          nextIndex -> entry.swap
      }
    }

    def map[A](f: T => A)(implicit func: Functor[P]): P[A] = func.map(underlying)(f)

    def flatMap[A](f: T => P[A])(implicit mapFlat: FlatMap[P]): P[A] = {
      mapFlat.flatMap(underlying)(f)
    }

    def fmap[A](f: T => A)(implicit func: Functor[P]): P[A] = func.map(underlying)(f)

    def filter(p: T => Boolean): Publisher[T] = {
      Publishers.filter[T](publisher)(p)
    }

    def concat(theRest: Publisher[T])(implicit ec: ExecutionContext): Publisher[T] = {
      Publishers.concat(publisher, theRest)
    }

    def combine(first: Publisher[T], theRest: Publisher[T]*)(implicit ec: ExecutionContext): Publisher[T] = {
      Publishers.combine(publisher, first, theRest: _*)
    }

    def combine(publishers: Iterable[Publisher[T]], fair: Boolean = true)(implicit ec: ExecutionContext): Publisher[T] = {
      Publishers.combine(publisher +: publishers.toList, fair)
    }

    def foldLeft[B](initialValue: B)(f: (B, T) => B): Publisher[B] = Publishers.foldLeft(publisher, initialValue)(f)

    def foldWith[I, H](initialValue: I)(f: (I, T) => (I, H)): Publisher[H] = {
      Publishers.foldWith[T, I, H](publisher, initialValue)(f)
    }

    def foreach(f: T => Unit): Future[Boolean] = {
      val sub = Subscribers.foreach(f)
      publisher.subscribe(sub)
      sub.result
    }

    def head(): Future[T] = publisher.subscribeWith(Subscribers.head[T]()).result

    def collect(limit: Long = Long.MaxValue): Future[List[T]] = {
      publisher.subscribeWith(Subscribers.collect[T](limit)).result
    }

    def count(limit: Long = Long.MaxValue)(implicit executor: ExecutionContext): Future[Int] = {
      collect(limit).map(_.size)
    }
  }

}
