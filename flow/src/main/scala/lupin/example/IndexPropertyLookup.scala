package lupin.example

/**
  * @tparam K the unique key
  * @tparam T the column type
  */
trait IndexPropertyLookup[K, T] extends Lookup[K, T] {

  /**
    * Return all the IDs which have this value
    *
    * @param value
    * @return
    */
  def idsForValue(value: T): Iterator[K]

  /**
    * This function assumes some stored data, and what the index would be for this value
    * (which may or may not exist).
    *
    * It returns an Option[Long] instead of a Long because there should always be a position this value could be
    * at.
    *
    * If the backing store is empty, then this function should always return 0
    *
    * @param value the value to index
    * @return the first index of this value
    */
  def indexForValue(value: T, ascending: Boolean): Long

  def valueAtIndex(index: Long): Option[T]

  def keyAtIndex(index: Long): Option[K]
}

object IndexPropertyLookup {

  class Impl[K, T] extends IndexPropertyLookup[K, T] {

    override def indexForValue(value: T, ascending: Boolean): Long = 0

    override def get(id: K): Option[T] = None

    override def idsForValue(value: T): Iterator[K] = ???

    override def valueAtIndex(index: SeqNo): Option[T] = ???

    override def keyAtIndex(index: SeqNo): Option[K] = ???
  }

  def apply[K, T](): IndexPropertyLookup[K, T] = new Impl[K, T]
}
