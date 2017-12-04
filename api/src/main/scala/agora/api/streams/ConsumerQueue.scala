package agora.api.streams

import java.util.{Queue => jQueue}

import cats.Semigroup
import com.typesafe.scalalogging.StrictLogging

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
  def apply[T](maxCapacity: Int) = {
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
      currentValue = previousValue
      if (currentRequested > 0) {
        currentRequested = (currentRequested - 1).max(0)
        currentValue.toList
      } else {
        Nil
      }
    }

    override def requested(): Long = currentRequested

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
        next = queue.poll()
      }
      currentRequested = i
      values.toList
    }
  }

}
