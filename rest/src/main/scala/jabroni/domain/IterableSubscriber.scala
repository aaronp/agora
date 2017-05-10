package jabroni.domain

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class IterableSubscriber[T](val initialRequestToTake: Int = 10)(implicit pollTimeout: FiniteDuration = 10.seconds)
  extends Subscriber[T]
    with StrictLogging {
  require(initialRequestToTake >= 0)

  private[this] sealed trait Next

  private[this] case class Value(t: T) extends Next

  private[this] case class Err(t: Throwable) extends Next

  private[this] case object Done extends Next

  private var subscriptionOpt = Option.empty[Subscription]

  private def bufferSize: Int = (initialRequestToTake * 2) + 1

  private val buffer = new ArrayBlockingQueue[Next](bufferSize)
  private val readBuffer = new ArrayBlockingQueue[Next](bufferSize)

  override def onError(t: Throwable): Unit = {
    logger.error(s"onError($t)")
    buffer.put(Err(t))
  }

  override def onComplete(): Unit = {
    logger.trace(s"onComplete")
    buffer.put(Done)
  }

  override def onNext(t: T): Unit = {
    logger.trace(s"onNext($t)")
    buffer.put(Value(t))
    logger.trace(s"buffer size after having put ($t) : ${buffer.size()}")
  }

  def subscription = subscriptionOpt.get

  override def onSubscribe(s: Subscription): Unit = {
    logger.trace(s"onSubscribe(...) requesting initial work of $initialRequestToTake")
    require(subscriptionOpt.isEmpty)
    subscriptionOpt = Option(s)
    subscription.request(initialRequestToTake)
  }

  object iterator extends Iterator[T] {
    override def next(): T = {
      logger.trace("Asking for next...")
      if (!hasNext) throw new NoSuchElementException
      val n = readBuffer.poll(pollTimeout.toMillis, MILLISECONDS)
      logger.trace(s"next is $n")
      n match {
        case Value(t) => t
        case Err(t) => throw t
        case Done => throw new NoSuchElementException
      }
    }

    @transient private var isDone = false

    override def hasNext: Boolean = {
      !readBuffer.isEmpty || (!isDone && pollForHasNext)
    }

    private def pollForHasNext: Boolean = {
      logger.trace(s"hasNext reading from write buffer of ${buffer.size}")
      val hnOpt = Option(buffer.poll(pollTimeout.toMillis, MILLISECONDS))
      logger.trace(s"hasNext read $hnOpt")
      hnOpt match {
        case Some(Done) =>
          isDone = true
          readBuffer.put(Done)
          false
        case Some(next) =>
          subscription.request(1)
          readBuffer.put(next)
          true
        case None =>
          isDone = true
          subscription.cancel()
          val exp = new TimeoutException(s"Timeout waiting for the next elm after $pollTimeout")
          readBuffer.put(Err(exp))
          true
      }
    }

  }

}