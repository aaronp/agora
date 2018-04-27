package lupin

import lupin.pub.join.{JoinPublisher, TupleUpdate}
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorDao}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext

object Publishers {

  def apply[T](dao: DurableProcessorDao[T])(implicit ec: ExecutionContext) = DurableProcessor[T](dao)

  def of[T](items: T*)(implicit ec: ExecutionContext): Publisher[T] = forList(items.toList)

  def forList[T](items: List[T])(implicit ec: ExecutionContext): Publisher[T] = {
    val p = DurableProcessor[T]()
    items.foreach(p.onNext)
    p.onComplete()
    p
  }


  /**
    * Joins the given publishers of the same type into a single [[Publisher]]
    *
    * @param first   the first publisher
    * @param second  the second publisher to combine
    * @param theRest any other publishers to combine
    * @tparam T
    * @return a single publisher which will consume elements from the other publishers and represent them as a single publisher
    */
  def combine[T](first: Publisher[T], second: Publisher[T], theRest: Publisher[T]*): Publisher[T] = {
    combine(first +: second +: theRest)
  }

  def combine[T](publishers: Iterable[Publisher[T]]): Publisher[T] = ???

  def join[A, B](left: Publisher[A], right: Publisher[B])(implicit ec: ExecutionContext): Publisher[TupleUpdate[A, B]] = {
    JoinPublisher(left, right)
  }

  /**
    * consumes the values from the first publisher until complete, then from the second.
    *
    * If the first is cancelled or errors then that is honored.
    */
  def concat[T](head: Publisher[T], tail: Publisher[T]): Publisher[T] = ???

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
