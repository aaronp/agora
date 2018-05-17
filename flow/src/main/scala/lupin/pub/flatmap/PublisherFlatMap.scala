package lupin.pub.flatmap

import cats.FlatMap
import lupin.Publishers
import lupin.sub.SubscriberDelegate
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * An implementation of FlatMap for a [[Publisher]]
  */
object PublisherFlatMap extends FlatMap[Publisher] {
  override def flatMap[A, B](underlying: Publisher[A])(f: A => Publisher[B]): Publisher[B] = {
    new FlatMapPublisher[A, B](underlying, f)
  }

  override def tailRecM[A, B](a: A)(f: A => Publisher[Either[A, B]]): Publisher[B] = {
    val eitherPub: Publisher[Either[A, B]] = f(a)
    import lupin.implicits._
    eitherPub.flatMap {
      case Left(a)  => tailRecM(a)(f)
      case Right(b) => Publishers.of(b)(ExecutionContext.Implicits.global)
    }
  }

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
