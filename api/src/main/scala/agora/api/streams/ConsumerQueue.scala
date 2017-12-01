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
    * @param n the additional n to request
    * @return the maximum ready elements to
    */
  def request(n: Long): List[T]

  /**
    * push a value on to the queue
    * @param value
    * @return the values now ready to publish to the subscription
    */
  def offer(value: T): List[T]
}

object ConsumerQueue {
  def apply[T](maxCapacity: Int) = {
    val queue = new java.util.concurrent.LinkedBlockingQueue[T](maxCapacity)
    new Instance(queue)
  }

  def apply[T: Semigroup](initialValue: Option[T] = None) = new ConflatingQueue(initialValue)

  class ConflatingQueue[T: Semigroup](initialValue: Option[T]) extends ConsumerQueue[T] with StrictLogging {

    import cats.syntax.semigroup._

    @volatile private var requested = 0L
    var currentValue: Option[T]     = initialValue

    private object Lock

    private def nextResult() = {
      if (requested > 0) {
        currentValue.toList
      } else {
        Nil
      }
    }

    override def request(n: Long): List[T] = Lock.synchronized {
      requested = requested + n
      nextResult()
    }

    override def offer(value: T): List[T] = Lock.synchronized {
      currentValue match {
        case None    => Option(value)
        case Some(v) => Option(v.combine(value))
      }
      nextResult()
    }
  }

  class Instance[T](queue: jQueue[T]) extends ConsumerQueue[T] with StrictLogging {

    private object Lock

    @volatile private var requested = 0L

    def request(n: Long): List[T] = {
      drain(requested + n)
    }

    def offer(value: T): List[T] = {
      Lock.synchronized {
        queue.add(value)
        if (requested > 0) {
          drain(requested)
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
      requested = i
      values.toList
    }
  }

}
