package lupin.pub.query

/**
  *
  * @param seqNo a 'version' field representing the last-seen version (sequence number update) for this value
  * @param index the index of the value
  * @param id the primary key for the given value at the given index
  * @param value the value at the given index
  * @tparam K the key type
  * @tparam T the value type
  */
case class IndexedEntry[K, T](seqNo: Long, index: Long, id: K, value: T)
