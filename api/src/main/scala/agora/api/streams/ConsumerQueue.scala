package agora.api.streams

import java.util.{Queue => jQueue}

import cats.Semigroup
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import scala.collection.mutable.ListBuffer

/**
  * An abstraction for holding the published values in an internal subscription before they are pulled from consumers.
  *
  * If we have a Semigroup for T, we might choose to conflate the values.
  *
  * We might otherwise just enqueue individual elements.
  *
  * @tparam T
  */
trait ConsumerQueue[T] {

  /**
    * request more -- this will trigger a check and return the 'more'
    *
    * @param n the additional n to request
    * @return the maximum ready elements to
    */
  def request(n: Long): List[T]

  /**
    * push a value on to the queue
    *
    * @param value
    * @return the values now ready to publish to the subscription
    */
  def offer(value: T): List[T]

  def requested(): Long
}

object ConsumerQueue {

  def jsonQueue(maxCapacity : Option[Int], discard : Option[Boolean]): ConsumerQueue[Json] = {
    maxCapacity match {
      case Some(capacity) =>
        if (discard.getOrElse(false)) {
          ConsumerQueue.keepLatest[Json](capacity)
        } else {
          ConsumerQueue.withMaxCapacity[Json](capacity)
        }
      case None =>
        ConsumerQueue[Json](None)
    }
  }

  /** @param capacity
    * @tparam T
    * @return a queue which will just keep the latest N elements and silently dump those which aren't consumed
    */
  def keepLatest[T](capacity: Int): ConsumerQueue[T] = {
    capacity match {
      case 1 => apply(None)(new RightBiasedSemigroup[T])
      case n =>
        val queue = new java.util.concurrent.LinkedBlockingQueue[T](n)
        new KeepNQueue(queue, n)
    }
  }

  def withMaxCapacity[T](maxCapacity: Int) = {
    val queue = new java.util.concurrent.LinkedBlockingQueue[T](maxCapacity)
    new Instance(queue)
  }

  def apply[T: Semigroup](initialValue: Option[T] = None) = new ConflatingQueue(initialValue)

  class ConflatingQueue[T: Semigroup](initialValue: Option[T]) extends ConsumerQueue[T] with StrictLogging {

    import cats.syntax.semigroup._

    @volatile private var currentRequested = 0L
    private var previousValue: Option[T]   = initialValue
    private var currentValue: Option[T]    = initialValue

    private object Lock

    override def request(n: Long): List[T] = Lock.synchronized {
      val newRequested = currentRequested + n
      if (newRequested > 0 && currentValue.nonEmpty) {
        currentRequested = newRequested - 1
        val list = currentValue.toList
        currentValue = None
        list
      } else {
        currentRequested = newRequested
        Nil
      }
    }

    override def offer(value: T): List[T] = Lock.synchronized {
      previousValue = previousValue match {
        case None => Option(value)
        case Some(old) =>
          val newValue = old.combine(value)
          Option(newValue)
      }
      if (currentRequested > 0) {
        currentRequested = (currentRequested - 1).max(0)
        currentValue = None
        previousValue.toList
      } else {
        currentValue = previousValue
        Nil
      }
    }

    override def requested(): Long = currentRequested

  }

  /**
    * Queue which just keeps the latest N items
    *
    * @param queue
    * @tparam T
    */
  class KeepNQueue[T](queue: jQueue[T], keep: Int) extends ConsumerQueue[T] with StrictLogging {

    private object Lock

    @volatile private var currentRequested = 0L

    def request(n: Long): List[T] = {
      drain(currentRequested + n)
    }

    override def requested(): Long = currentRequested

    def offer(value: T): List[T] = {
      Lock.synchronized {
        if (queue.size() == keep) {
          queue.poll()
        }
        queue.add(value)
        if (currentRequested > 0) {
          drain(currentRequested)
        } else {
          Nil
        }
      }
    }

    private def drain(totalRequested: Long): List[T] = {
      var i = totalRequested

      val values  = ListBuffer[T]()
      var next: T = queue.poll()
      while (i > 0 && next != null) {
        values += next
        i = i - 1
        if (i > 0) {
          next = queue.poll()
        }
      }
      currentRequested = i
      values.toList
    }
  }

  class Instance[T](queue: jQueue[T]) extends ConsumerQueue[T] with StrictLogging {

    private object Lock

    @volatile private var currentRequested = 0L

    def request(n: Long): List[T] = {
      drain(currentRequested + n)
    }

    override def requested(): Long = currentRequested

    def offer(value: T): List[T] = {
      Lock.synchronized {
        queue.add(value)
        if (currentRequested > 0) {
          drain(currentRequested)
        } else {
          Nil
        }
      }
    }

    private def drain(totalRequested: Long): List[T] = {
      var i = totalRequested

      val values  = ListBuffer[T]()
      var next: T = queue.poll()
      while (i > 0 && next != null) {
        i = i - 1
        values += next
        if (i > 0) {
          next = queue.poll()
        }
      }
      currentRequested = i
      values.toList
    }
  }

  private class RightBiasedSemigroup[T] extends Semigroup[T] {
    override def combine(x: T, y: T): T = y
  }

}
