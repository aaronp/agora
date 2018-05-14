package lupin.pub.query

case class IndexQueryResult[K, T](seqNo: Long, id: K, index: Long, value: T)
