package agora.domain

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.Iterator
import scala.util.Try

/**
  * An iterator which will invoke the given 'closeMe' thunk when it is exhausted
  * It also implements 'AutoClosable' should it need to be closed early
  */
case class CloseableIterator[T](iter: Iterator[T])(closeMe: => Unit) extends Iterator[T] with AutoCloseable {

  // a means of closing the thunk only once, and forcing its execution via a lazy or (||) chain
  private lazy val closed = {
    Try(closeMe)
    false
  }

  private val forceClosed = new AtomicBoolean(false)

  override def close = forceClosed.compareAndSet(closed, true)

  override def hasNext: Boolean = {
    try {
      forceClosed.get == false && iter.hasNext || closed
    } catch {
      case _: Throwable => closed
    }
  }

  override def take(n: Int) = {
    CloseableIterator(iter.take(n))(closeMe)
  }

  override def slice(from: Int, until: Int): Iterator[T] = {
    CloseableIterator(iter.slice(from, until))(closeMe)
  }

  override def next(): T =
    try {
      if (!hasNext) throw new NoSuchElementException
      iter.next()
    } catch {
      case e: Throwable =>
        require(closed == false)
        throw e
    }
}
