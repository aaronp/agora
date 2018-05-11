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
  def apply[T](iter: Iterator[T])(implicit ec: ExecutionContext) = {
    unfold[T] {
      case _ =>
        if (!iter.hasNext) {
          None -> false
        } else {
          Option(iter.next) -> iter.hasNext
        }
    }
  }

  def sequenced[T](dao: DurableProcessorDao[T] = DurableProcessorDao[T]())(implicit ec: ExecutionContext): DurableProcessorInstance[T] = {
    DurableProcessor[T](dao)
  }

  def of[T](items: T*)(implicit ec: ExecutionContext) = apply(items.iterator)

  def forValues[T](items: Iterable[T])(implicit ec: ExecutionContext): Publisher[T] = apply(items.iterator)

  /**
    * publish the values until the function returns a (nextValue, hasNext) where the second tuple value is false
    *
    * @param createNext a function which, given the previous value, can return an optional next value and 'hasNext' result
    * @param ec
    * @tparam T
    * @return
    */
  def unfold[T](createNext: Option[T] => (Option[T], Boolean))(implicit ec: ExecutionContext): Publisher[T] = {
    new DurableProcessorInstance[T](DurableProcessorDao[T]()) {
      var currentlyRequested = 0L
      var previous = Option.empty[T]

      override def onRequest(n: Long): Unit = {
        require(n > 0)
        currentlyRequested = currentlyRequested + n
        if (currentlyRequested < 0) {
          currentlyRequested = Long.MaxValue
        }
        var hasNext = true
        do {
          val (newPrevious, newHasNext) = createNext(previous)
          hasNext = newHasNext && newPrevious.nonEmpty
          previous = newPrevious
          previous.foreach(onNext)
          currentlyRequested = currentlyRequested - 1
        } while (hasNext && currentlyRequested > 0)

        if (!hasNext) {
          onComplete()
        }
      }
    }.valuesPublisher()
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
  def combine[T](first: Publisher[T], second: Publisher[T], theRest: Publisher[T]*)(implicit ec: ExecutionContext): Publisher[T] = {
    combine(first +: second +: theRest)
  }

  def combine[T](publishers: Iterable[Publisher[T]], fair: Boolean = true)(implicit ec: ExecutionContext): Publisher[T] = {
    val combined: CollatingPublisher[Int, T] = CollatingPublisher[Int, T](fair = fair)
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

  def foldWith[A, B, T](underlying: Publisher[A], initialValue: B)(f: (B, A) => (B, T)): Publisher[T] = {
    new Publisher[T] {
      override def subscribe(mappedSubscriber: Subscriber[_ >: T]): Unit = {
        object WrapperForA extends Subscriber[A] {
          var combinedValue = initialValue

          override def onSubscribe(sInner: Subscription): Unit = {
            mappedSubscriber.onSubscribe(sInner)
          }

          override def onNext(t: A): Unit = {
            val (newCombined, next) = f(combinedValue, t)
            combinedValue = newCombined
            mappedSubscriber.onNext(next)
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

  def foldLeft[A, B](underlying: Publisher[A], initialValue: B)(f: (B, A) => B): Publisher[B] = {
    foldWith[A, B, B](underlying, initialValue) {
      case (b, a) =>
        val nextB = f(b, a)
        nextB -> nextB
    }
  }

  def filter[A](underlying: Publisher[A])(f: A => Boolean): Publisher[A] = {
    new Publisher[A] {
      override def subscribe(mappedSubscriber: Subscriber[_ >: A]): Unit = {
        object WrapperForA extends Subscriber[A] {
          private var ourSubscription: Subscription = null

          override def onSubscribe(sInner: Subscription): Unit = {
            mappedSubscriber.onSubscribe(sInner)
            ourSubscription = sInner
          }

          override def onNext(t: A): Unit = {
            if (f(t)) {
              mappedSubscriber.onNext(t)
            } else if (ourSubscription != null) {
              ourSubscription.request(1)
            }
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
