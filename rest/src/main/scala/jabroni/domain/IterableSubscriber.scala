package jabroni.domain

import java.util.concurrent.ArrayBlockingQueue

import akka.stream.Materializer
import akka.stream.scaladsl.{Framing, Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.TimeoutException
import scala.concurrent.duration._

object IterableSubscriber {
  def iterate(dataBytes: Source[ByteString, Any],
              maximumFrameLength: Int,
              allowTruncation: Boolean = false)(implicit mat: Materializer): Iterator[String] = {

    val linesFlow = Framing.delimiter(ByteString("\n"), maximumFrameLength, allowTruncation = allowTruncation)
    val text = dataBytes.via(linesFlow.map(_.utf8String))
    iterate(text)
  }

  def iterate(linesFlow: Source[String, Any])(implicit mat: Materializer): Iterator[String] = {
    val subscriber = new IterableSubscriber[String]()
    linesFlow.runWith(Sink.fromSubscriber(subscriber))
    subscriber.iterator
  }
}

class IterableSubscriber[T](val initialRequestToTake: Int = 10)(implicit pollTimeout: FiniteDuration = 10.seconds)
  extends Subscriber[T] {
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
    buffer.put(Err(t))
  }

  override def onComplete(): Unit = {
    buffer.put(Done)
  }

  override def onNext(t: T): Unit = {
    buffer.put(Value(t))
  }

  def subscription = subscriptionOpt.get

  override def onSubscribe(s: Subscription): Unit = {
    require(subscriptionOpt.isEmpty)
    subscriptionOpt = Option(s)
    subscription.request(initialRequestToTake)
  }

  object iterator extends Iterator[T] {
    override def next(): T = {
      if (!hasNext) throw new NoSuchElementException
      val n = readBuffer.poll(pollTimeout.toMillis, MILLISECONDS)
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
      val hnOpt = Option(buffer.poll(pollTimeout.toMillis, MILLISECONDS))
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