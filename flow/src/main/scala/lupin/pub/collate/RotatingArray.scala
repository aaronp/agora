package lupin.pub.collate

import java.util

/**
  * This class is NOT thread safe
  *
  * The idea is that each call to 'iterator' will iterate over the elements in a rotating order, e.g.
  * {{{
  * [1,2,3]
  * [2,3,1]
  * [3,1,2]
  * [1,2,3]
  * }}}
  *
  * @param initialElms
  * @tparam T
  */
class RotatingArray[T](initialElms: Iterable[T] = Iterable.empty) {

  private val array = new util.ArrayList[T](initialElms.size)
  initialElms.foreach(array.add)

  def remove(value: T) = {
    array.remove(value)
    this
  }

  def add(value: T) = {
    array.add(value)
    this
  }

  private var lastIteratorIndex = -1

  /** @return an iterator over the array and advance the iterator so that the next time it's called it'll be of different elements
    */
  def iterator: Iterator[T] = new Iterator[T] {
    private val len = array.size
    private val endIndex = if (len == 0) -1 else ((lastIteratorIndex + 1) % len)
    lastIteratorIndex = endIndex
    private var currentIndex = if (len == 0) -1 else ((endIndex + 1) % len)
    private var lastRead = false

    override def hasNext: Boolean = len > 0 && !lastRead

    override def next(): T = {
      require(hasNext)
      val idx = currentIndex
      lastRead = currentIndex == endIndex
      currentIndex = (currentIndex + 1) % len
      array.get(idx)
    }
  }

}
