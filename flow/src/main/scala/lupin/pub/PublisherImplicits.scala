package lupin.pub

import cats.{FlatMap, Functor}
import lupin.pub.flatmap.FlatMapPublisher
import lupin.sub.SubscriberDelegate
import lupin.{Publishers, Subscribers}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{ExecutionContext, Future}

trait LowPriorityPublisherImplicits {

  implicit def asRichPublisher[T, P[_] <: Publisher[_]](pub: P[T]): PublisherImplicits.RichPublisher[T, P] = new PublisherImplicits.RichPublisher[T, P](pub)

  implicit def asRichPublisher2[T](pub: Publisher[T]): PublisherImplicits.RichPublisher[T, Publisher] = {
    asRichPublisher(pub)
  }

  object PublisherFlatMap extends FlatMap[Publisher] {
    override def flatMap[A, B](underlying: Publisher[A])(f: A => Publisher[B]): Publisher[B] = {

      new FlatMapPublisher[A, B](underlying, f)
    }

    override def tailRecM[A, B](a: A)(f: A => Publisher[Either[A, B]]): Publisher[B] = ???

    override def map[A, B](underlying: Publisher[A])(f: A => B): Publisher[B] = {
      new Publisher[B] {
        override def subscribe(mappedSubscriber: Subscriber[_ >: B]): Unit = {
          underlying.subscribe(new SubscriberDelegate[A](mappedSubscriber) {
            override def onNext(t: A): Unit = {
              mappedSubscriber.onNext(f(t))
            }
          })
        }
      }
    }
  }

  //  implicit def asFunctor: Functor[Publisher] = PublisherFlatMap

  implicit def asFlatMap: FlatMap[Publisher] = PublisherFlatMap
}

object PublisherImplicits extends LowPriorityPublisherImplicits {

  class RichPublisher[T, P[_] <: Publisher[_]](val underlying: P[T]) extends AnyVal {

    private def publisher = underlying.asInstanceOf[Publisher[T]]

    def withSubscriber[A >: T, S <: Subscriber[A]](s: S): S = {
      publisher.subscribe(s)
      s
    }

    def map[A](f: T => A)(implicit func: Functor[P]): P[A] = func.map(underlying)(f)
    def mapP[A](f: T => A)(implicit func: Functor[Publisher]): Publisher[A] = func.map(publisher)(f)

    def flatMap[A](f: T => P[A])(implicit semi: FlatMap[P]): P[A] = {
      semi.flatMap(underlying)(f)
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
