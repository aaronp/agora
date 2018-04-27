package lupin.example

import org.reactivestreams.Publisher

import scala.annotation.tailrec

/**
  * We should have one of these per property. It might just store a sorted collection of property-to-Ids buckets,
  * or the the full objects themselves
  *
  * @tparam T typically an ID/value pair, such as (Long, String) of e.g. (myEntity.id, myEntity.someField)
  */
trait IndexedOrder[T] {
  type Self <: IndexedOrder[T]

  /** @param value
    * @return the index and updated IndexedOrder where the value has been inserted
    */
  def upsert(value: T): (Long, Self)

  /** @param value
    * @return the index of the given value
    */
  def indexOf(value: T): Long

  def valuesAtIndex(index: IndexSelection): Publisher[T]
}

object IndexedOrder {
  def apply[T, K](implicit accessor: Accessor.Aux[T, K], keyOrdering: Ordering[K], valueOrdering: Ordering[T]): IndexedOrder[T] = new Impl[T, K]

  class Bucket[T] {
    private var values: Set[T] = Set.empty

    def size = values.size

    def add(value: T) = {
      values = values + value
      this
    }

    def toSet = values
  }

  class Impl[T, K](implicit accessor: Accessor.Aux[T, K], keyOrdering: Ordering[K], valueOrdering: Ordering[T]) extends IndexedOrder[T] {
    override type Self = Impl[T, K]

    import Ordering.Implicits._

    // this is very inefficient, but just gives us something working for now
    private var valuesByKey = List[(K, List[T])]()

    private object Lock

    @tailrec
    private def insert(revHead: List[(K, List[T])], remaining: List[(K, List[T])], key: K, value: T, count: Long): (Long, List[(K, List[T])]) = {

      remaining match {
        case Nil => count -> ((key -> List(value)) :: revHead).reverse
        case (head@(headKey, headList)) :: tail =>

          val res = keyOrdering.compare(headKey, key)
          if (res < 0) {
            insert(head :: revHead, tail, key, value, count + headList.size)
          } else if (res == 0) {
            // insert into this list
            val (prevList: List[T], afterList: List[T]) = headList.span(_ < value)
            val newList: List[T] = prevList ::: (value :: afterList)

            val newEntry: (K, List[T]) = (headKey, newList)
            val newTail = newEntry :: tail.asInstanceOf[List[(K, List[T])]]
            val newSorted: List[(K, List[T])] = revHead.reverse ::: newTail
            val pos = count + prevList.size
            pos -> newSorted
          } else {
            // this key is greater than ours, so we can insert a new entry
            val newPrefix = ((key -> List(value)) :: revHead).reverse
            count -> (newPrefix ::: remaining)
          }
      }

    }

    override def upsert(value: T): (Long, Self) = {
      val key: K = accessor.get(value)
      Lock.synchronized {
        val (idx, newList) = insert(Nil, valuesByKey, key, value, 0)
        valuesByKey = newList
        (idx, this)
      }
    }

    override def valuesAtIndex(index: IndexSelection) = {

      ???
    }
    override def indexOf(value: T): Long = {
      val key: K = accessor.get(value)
      val snapshot = Lock.synchronized(valuesByKey)
      snapshot.foldLeft(0) {
        case (count, (nextKey, nextList)) if nextKey < key => count + nextList.size
        case (count, (nextKey, nextList)) if nextKey equiv key =>
          nextList.indexOf(value) match {
            case n if n < 0 => -1
            case idx => count + idx
          }
        case (_, (nextKey, _)) if nextKey > key => -1
      }

    }
  }

}
