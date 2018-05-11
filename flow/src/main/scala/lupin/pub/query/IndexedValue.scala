package lupin.pub.query

case class IndexedValue[K, T](seqNo: Long, id: K, indexOp: IndexOperation[T])

