package lupin.pub

import lupin.{Publishers, Subscribers}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

trait PublisherImplicits {

  implicit def asRichPublisher[T](pub: Publisher[T]) = new PublisherImplicits.RichPublisher[T](pub)

}

object PublisherImplicits {

  class RichPublisher[T](val publisher: Publisher[T]) extends AnyVal {
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

    def foreach(f : T => Unit) = publisher.subscribe(Subscribers.foreach(f))
  }

}