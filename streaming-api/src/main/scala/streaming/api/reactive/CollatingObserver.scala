package streaming.api.reactive

import com.typesafe.scalalogging.StrictLogging
import monix.execution.atomic.AtomicLong
import monix.execution.rstreams.Subscription
import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.Future

abstract class CollatingObserver[T >: Null <: AnyRef](combine : (T , T) => T)(implicit scheduler : Scheduler)
  extends Observer[T]
    with Publisher[T]
    with StrictLogging {

  private var last : T = null.asInstanceOf[T]

  private @volatile var subscriber : Subscriber[_ >: T] = null.asInstanceOf[Subscriber[_ >: T]]
  private @volatile var ack : Future[Ack] = Ack.Continue
  private val requested = new AtomicLong(0)
  private object RequestedLock


  override def onNext(elem: T): Future[Ack] = {
    last match {
      case null => last = elem
      case value => last = combine(value, elem)
    }

      while (requested.get == 0) {
      RequestedLock.synchronized {
        logger.debug(s"zero requested, waiting indefinitely...")
          RequestedLock.wait()
        logger.debug(s"got notified of requested: ${requested.get}")
        }
    }

    ack
  }

  override def onError(ex: Throwable): Unit = {
    val s= subscriber
    if (s != null) {
      s.onError(ex)
    }
  }

  override def onComplete(): Unit = {
    val s = subscriber
    if (s != null) {
      s.onComplete()
    }
  }
  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    require(subscriber == null, "Already subscribed")
    subscriber = s
    s.onSubscribe(new Subscription {
      override def request(n: Long): Unit = {
        val before = requested.getAndAdd(n)
        if (before == 0) {
          logger.debug(s"requested $n from zero, notifying")
          RequestedLock.synchronized {
            RequestedLock.notify()
          }
        }
        if (before + n < 0) {
          requested.set(Long.MaxValue)
        }
      }
      override def cancel(): Unit         = {
        logger.debug(s"cancelling subscription")
        ack = Ack.Stop
        requested.set(Long.MinValue)
        RequestedLock.synchronized {
          RequestedLock.notify()
        }
        subscriber = null
      }
    })
  }
}