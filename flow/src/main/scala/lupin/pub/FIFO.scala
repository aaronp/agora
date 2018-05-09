package lupin.pub

import java.util.concurrent.LinkedBlockingQueue

import cats.kernel.Semigroup

/**
  * Provides a generic, minimal implementation of a queue for elements sent between a publisher and a subscriber
  *
  * @tparam T
  */
trait FIFO[T] {

  /**
    * This should be a non-blocking operation to enqueue a value to be consumed by the blocking 'pop' operation.
    *
    * null may be used to represent the end of the queue.
    *
    * @param value the value to enqueue
    * @return true to signify the enqueue succeeded w/o going over capacity
    */
  def enqueue(value: T): Boolean

  /**
    * A blocking operation to take an element from the queue.
    *
    * 'pop' should block until a value is enqueued, and a null value may be returned to signify there
    * are no more elements
    *
    * @return the next enqueued value, or null if there are no more elements
    */
  def pop(): T
}

object FIFO {

  class FilteredFIFO[T](queue: FIFO[T], predicate: T => Boolean) extends FIFO[T] {
    override def enqueue(value: T): Boolean =
      if (predicate(value)) {
        queue.enqueue(value)
      } else {
        true
      }

    override def pop(): T = queue.pop()
  }

  def filter[T](queue: FIFO[T])(predicate: T => Boolean): FIFO[T] = new FilteredFIFO[T](queue, predicate)

  /**
    * @tparam T the value type
    * @return a FIFO which will always overwrite the last value -- popping will still block after the latest value is read
    */
  def mostRecent[T]: FIFO[Option[T]] = new FIFO[Option[T]] {

    private object Lock

    private var last = Option.empty[T]

    override def enqueue(value: Option[T]): Boolean = {
      Lock.synchronized {
        last = value
        Lock.notify()
      }
      true
    }

    override def pop(): Option[T] = {
      Lock.synchronized {
        if (last == None) {
          Lock.wait()
        }
        val opt = last
        last = None
        opt
      }
    }
  }

  /** Creates a FIFO based on the semigroup
    *
    * @tparam T the FIFO type
    * @return a FIFO which will collate the elements
    */
  def collated[T: Semigroup]: FIFO[Option[T]] = new FIFO[Option[T]] {
    var last = Option.empty[T]

    private object Lock

    private val semigroup = Semigroup[T]

    override def enqueue(value: Option[T]): Boolean = {
      Lock.synchronized {
        value match {
          case None => last = None
          case Some(next) =>
            last = last.fold(value) { prev =>
              Option(semigroup.combine(prev, next))
            }
            Lock.notify()
        }
      }
      true
    }
    override def pop(): Option[T] = {
      Lock.synchronized {
        if (last.isEmpty) {
          Lock.wait()
        }
        val opt = last
        last = None
        opt
      }
    }
  }

  /** @param capacity the queue capacity
    * @tparam T
    * @return A FIFO backed by a blocking queue of the given capacity
    */
  def apply[T](capacity: Int = Int.MaxValue): FIFO[T] = new FIFO[T] {
    val queue = new LinkedBlockingQueue[T](capacity)

    override def enqueue(value: T) = queue.offer(value)

    override def pop(): T = queue.take()
  }
}
