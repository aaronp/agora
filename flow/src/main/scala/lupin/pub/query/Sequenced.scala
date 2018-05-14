package lupin.pub.query

case class Sequenced[T](seqNo: Long, data: T)
