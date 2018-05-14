package lupin.pub

import lupin.{Publishers, Subscribers}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{ExecutionContext, Future}

trait PublisherImplicits {

  implicit def asRichPublisher[T](pub: Publisher[T]) = new PublisherImplicits.RichPublisher[T](pub)

}

object PublisherImplicits {

  class RichPublisher[T](val publisher: Publisher[T]) extends AnyVal {

    def withSubscriber[A >: T, S <: Subscriber[A]](s : S) : S = {
      publisher.subscribe(s)
      s
    }

    def map[A](f: T => A): Publisher[A] = Publishers.map[T, A](publisher)(f)

    def filter(p: T => Boolean): Publisher[T] = Publishers.filter(publisher)(p)

    def concat(theRest: Publisher[T])(implicit ec: ExecutionContext): Publisher[T] = Publishers.concat(publisher, theRest)

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

    def collect(limit: Long = Long.MaxValue): Future[List[T]] = {
      val sub = Subscribers.collect[T](limit)
      publisher.subscribe(sub)
      sub.result
    }

    def count(limit: Long = Long.MaxValue)(implicit executor: ExecutionContext): Future[Int] = {
      collect(limit).map(_.size)
    }
  }

}
