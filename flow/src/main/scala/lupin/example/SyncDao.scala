package lupin.example

/**
  * Represents a synchronous DAO which can be both updated w/ new values and retrieve values based on a key
  *
  * We'll need this to get the full type T for some Key 'K'.
  *
  * As a user changes their view, we'll have to go from
  *
  * (Property, RowIndex) => T
  *
  * @tparam K
  */
trait SyncDao[K, Result] extends Lookup[K, Result] {
  type Self <: SyncDao[K, Result]

  def update(value: Result): Self

}

object SyncDao {

  class Buffer[K, T](map: Map[K, T])(implicit idx: Accessor.Aux[T, K]) extends SyncDao[K, T] {
    type Self = Buffer[K, T]

    override def update(value: T): Buffer[K, T] = {
      val key = idx.get(value)
      new Buffer(map.updated(key, value))
    }

    override def get(id: K): Option[T] = map.get(id)
  }

  def apply[K, T](implicit idx: Accessor.Aux[T, K]) = new Buffer[K, T](Map.empty)
}
