package lupin.pub.query


sealed trait IndexOperation[T]

case class NewIndex[T](index: Long, value: T) extends IndexOperation[T]

case class MovedIndex[T](from: Long, to: Long, value: T) extends IndexOperation[T]

case class RemovedIndex[T](index: Long, value: T) extends IndexOperation[T]
