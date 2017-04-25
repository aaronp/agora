package jabroni.api.exchange

import scala.collection.mutable.ListBuffer


trait InputDao[T] {
  def save(input: T)
  def remove(input : T)
  def matching(n: Int, filter: T => Boolean): Iterator[T]
}
object InputDao {
  def apply[T]() = new Buffer[T]()

  class Buffer[T]() extends InputDao[T] {
    private val saved = ListBuffer[T]()
    override def save(input: T): Unit = saved += input
    override def remove(input: T): Unit = {
      val idx = saved.indexOf(input)
      saved.remove(idx)
    }

    override def matching(n: Int, predicate: T => Boolean): Iterator[T] = {
      require(n > 0, s"asked to take $n")
      saved.filter(predicate).take(n).iterator
    }
  }
}