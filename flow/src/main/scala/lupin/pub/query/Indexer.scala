package lupin.pub.query

import lupin.data.Accessor
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

object Indexer {

  case class Sequenced[T](seqNo: Long, data: T)


  case class Indexed[K, T](seqNo: Long, id: K, indexOp: IndexOperation[T])


  import lupin.implicits._

  def crud[K, T](data: Publisher[T],
                 inputDao: SyncDao[K, T] = null)(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext): Publisher[(CrudOperation[K], T)] = {
    val dao = if (inputDao == null) {
      SyncDao[K, T](accessor)
    } else {
      inputDao
    }

    data.foldWith[SyncDao[K, T], (CrudOperation[K], T)](dao) {
      case (db, next) =>
        val (crudOp, newDao) = db.update(next)
        newDao -> (crudOp, next)
    }
  }



  def apply[K, T: Ordering](seqNoDataAndOp: Publisher[Sequenced[(CrudOperation[K], T)]]): Publisher[Indexed[K, T]] = {

    class SortedEntry(val key: K, val value: T) {
      override def equals(other: Any) = other match {
        case se: SortedEntry => key == se.key
        case _ => false
      }

      override def hashCode(): Int = key.hashCode()
    }

    def insert(sortedValues: Vector[SortedEntry], entry: SortedEntry): Vector[SortedEntry] = {
      //        values :+ entry
      import Ordering.Implicits._
      val (before, after) = sortedValues.span(_.value < entry.value)
      before ++: (entry +: after)
    }

    case class IndexStore(values: Vector[SortedEntry] = Vector()) {

      def index(seqNo: Long, data: T, op: CrudOperation[K]): (IndexStore, Indexed[K, T]) = {
        val entry = new SortedEntry(op.key, data)
        op match {
          case Create(key) =>
            require(!values.contains(entry))
            val newStore = copy(values = insert(values, entry))
            val idx = values.indexOf(entry)
            newStore -> Indexed[K, T](seqNo, key, NewIndex(idx, data))
          case Update(key) =>
            require(values.contains(entry))

            val oldIndex = values.indexOf(entry)
            val removedValues = values diff (List(entry))
            require(removedValues.size == values.size - 1)

            val newStore = copy(values = insert(removedValues, entry))

            val idx = values.indexOf(entry)
            newStore -> Indexed[K, T](seqNo, key, MovedIndex(oldIndex, idx, data))
          case Delete(key) =>
            require(values.contains(entry))

            val oldIndex = values.indexOf(entry)
            val removedValues = values diff (List(entry))
            require(removedValues.size == values.size - 1)

            val newStore = copy(values = removedValues)
            newStore -> Indexed[K, T](seqNo, key, RemovedIndex(oldIndex, data))
        }
      }

      this
    }

    seqNoDataAndOp.foldWith(new IndexStore) {
      case (store, Sequenced(seqNo, (op, data))) =>
        store.index(seqNo, data, op)
    }
  }
}