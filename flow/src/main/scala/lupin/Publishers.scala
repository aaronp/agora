package lupin

import lupin.pub.collate.CollatingPublisher
import lupin.pub.concat.ConcatPublisher
import lupin.pub.join.{JoinPublisher, TupleUpdate}
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorDao, DurableProcessorInstance}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext

object Publishers {

  /**
    * Creates a publisher which contains the given elements. The iterator won't be consumed until it is subscribed
    * to/consumed
    *
    * @param iter
    * @param ec
    * @tparam T
    * @return a Publisher of the given elements
    */
  def apply[T](iter: Iterator[T])(implicit ec: ExecutionContext): Publisher[T] = {
    new DurableProcessorInstance[T](new DurableProcessor.Args[T](DurableProcessorDao[T]())) {
      override def onRequest(n: Long): Unit = {
        var i = if (n > Int.MaxValue) {
          Int.MaxValue
        } else {
          n.toInt
        }
        while (i > 0 && iter.hasNext) {
          onNext(iter.next())
          i = i - 1
        }
        if (!iter.hasNext) {
          onComplete()
        }
      }
    }
  }

  def apply[T](dao: DurableProcessorDao[T] = DurableProcessorDao[T]())(implicit ec: ExecutionContext) = DurableProcessor[T](dao)

  def of[T](items: T*)(implicit ec: ExecutionContext): Publisher[T] = apply(items.iterator)

  def forValues[T](items: Iterable[T])(implicit ec: ExecutionContext): Publisher[T] = apply(items.iterator)

  /**
    * Joins the given publishers of the same type into a single [[Publisher]]
    *
    * @param first   the first publisher
    * @param second  the second publisher to combine
    * @param theRest any other publishers to combine
    * @tparam T
    * @return a single publisher which will consume elements from the other publishers and represent them as a single publisher
    */
  def combine[T](first: Publisher[T], second: Publisher[T], theRest: Publisher[T]*)(implicit ec: ExecutionContext): Publisher[T] = {
    combine(first +: second +: theRest)
  }

  def combine[T](publishers: Iterable[Publisher[T]])(implicit ec: ExecutionContext): Publisher[T] = {
    val combined: CollatingPublisher[Int, T] = CollatingPublisher[Int, T]()
    publishers.zipWithIndex.foreach {
      case (pub, idx) =>
        val sub = combined.newSubscriber(idx)
        pub.subscribe(sub)
    }

    Publishers.map(combined)(_._2)
  }

  def join[A, B](left: Publisher[A], right: Publisher[B])(implicit ec: ExecutionContext): Publisher[TupleUpdate[A, B]] = {
    JoinPublisher(left, right)
  }

  /**
    * consumes the values from the first publisher until complete, then from the second.
    *
    * If the first is cancelled or errors then that is honored.
    */
  def concat[T](head: Publisher[T], tail: Publisher[T])(implicit ec: ExecutionContext): Publisher[T] = {
    ConcatPublisher.concat(head, tail)
  }

  def concat[T](head: Publisher[T])(subscribeNext: Subscriber[T] => Unit)(implicit ec: ExecutionContext): Publisher[T] = {
    ConcatPublisher.concat(head)(subscribeNext)
  }

  def map[A, B](underlying: Publisher[A])(f: A => B): Publisher[B] = {
    new Publisher[B] {
      override def subscribe(mappedSubscriber: Subscriber[_ >: B]): Unit = {
        object WrapperForA extends Subscriber[A] {
          override def onSubscribe(sInner: Subscription): Unit = {
            mappedSubscriber.onSubscribe(sInner)
          }

          override def onNext(t: A): Unit = {
            mappedSubscriber.onNext(f(t))
          }

          override def onError(t: Throwable): Unit = {
            mappedSubscriber.onError(t)
          }

          override def onComplete(): Unit = {
            mappedSubscriber.onComplete()
          }
        }
        underlying.subscribe(WrapperForA)
      }
    }
  }
}
