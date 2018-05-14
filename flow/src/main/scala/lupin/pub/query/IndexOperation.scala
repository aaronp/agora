package lupin.pub.query

sealed trait IndexOperation[T]

object IndexOperation {
  def newIndex[T](index: Long, value: T): IndexOperation[T] = NewIndex(index, value)

  def movedIndex[T](from: Long, to: Long, value: T): IndexOperation[T] = MovedIndex(from, to, value)

  def removedIndex[T](index: Long, value: T): IndexOperation[T] = RemovedIndex(index, value)
}

case class NewIndex[T](index: Long, value: T) extends IndexOperation[T]

case class MovedIndex[T](from: Long, to: Long, value: T) extends IndexOperation[T]

case class RemovedIndex[T](index: Long, value: T) extends IndexOperation[T]
