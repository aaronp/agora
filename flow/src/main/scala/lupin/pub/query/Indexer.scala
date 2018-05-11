package lupin.pub.query

import lupin.Publishers
import lupin.data.Accessor
import lupin.example.{IndexRange, IndexSelection, SpecificIndices}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

trait Indexer[K, T] {
  def index(seqNo: Long, data: T, op: CrudOperation[K]): (Indexer[K, T], IndexedValue[K, T])

}

object Indexer {

  import lupin.implicits._

  /**
    * Folds the data feed over the given [[SyncDao]] used to write them down and return a [[CrudOperation]] result
    * @param data
    * @param inputDao
    * @param accessor
    * @param execContext
    * @tparam K
    * @tparam T
    * @return a publisher of the operations w/ the values flowing through it
    */
  def crud[K, T](data: Publisher[T],
                 inputDao: SyncDao[K, T] = null)(implicit accessor: Accessor[T, K], execContext: ExecutionContext): Publisher[(CrudOperation[K], T)] = {
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


  def apply[K, T: Ordering](seqNoDataAndOp: Publisher[Sequenced[(CrudOperation[K], T)]],
                            indexer: Indexer[K, T]): Publisher[IndexedValue[K, T]] = {
    seqNoDataAndOp.foldWith(indexer) {
      case (store, Sequenced(seqNo, (op, data))) => store.index(seqNo, data, op)
    }
  }


  private class SortedEntry[K, T](val key: K, val seqNo: Long, val value: T) {
    override def equals(other: Any) = other match {
      case se: SortedEntry[_, _] => key == se.key
      case _ => false
    }

    override def hashCode(): Int = key.hashCode()
  }

  private def insert[K, T: Ordering](sortedValues: Vector[SortedEntry[K, T]], entry: SortedEntry[K, T]): Vector[SortedEntry[K, T]] = {
    import Ordering.Implicits._
    val (before, after) = sortedValues.span(_.value < entry.value)
    before ++: (entry +: after)
  }


  type QueryIndexer[K, T] = Indexer[K, T] with IndexQuerySource[K, T]
  def slowInMemoryIndexer[K, T: Ordering](implicit executionContext: ExecutionContext): QueryIndexer[K, T] = new SlowInMemoryStore

  private case class SlowInMemoryStore[K, T: Ordering](values: Vector[SortedEntry[K, T]] = Vector())(implicit executionContext: ExecutionContext) extends Indexer[K, T] with IndexQuerySource[K, T] {

    override def index(seqNo: Long, data: T, op: CrudOperation[K]) = {
      val entry = new SortedEntry(op.key, seqNo, data)
      op match {
        case Create(key) =>
          require(!values.contains(entry))
          val newStore = copy(values = insert(values, entry))
          val idx = newStore.values.indexOf(entry)
          newStore -> IndexedValue[K, T](seqNo, key, NewIndex(idx, data))
        case Update(key) =>
          require(values.contains(entry))

          val oldIndex = values.indexOf(entry)
          val removedValues = values diff (List(entry))
          require(removedValues.size == values.size - 1)

          val newStore = copy(values = insert(removedValues, entry))

          val idx = newStore.values.indexOf(entry)
          newStore -> IndexedValue[K, T](seqNo, key, MovedIndex(oldIndex, idx, data))
        case Delete(key) =>
          require(values.contains(entry))

          val oldIndex = values.indexOf(entry)
          val removedValues = values diff (List(entry))
          require(removedValues.size == values.size - 1)

          val newStore = copy(values = removedValues)
          newStore -> IndexedValue[K, T](seqNo, key, RemovedIndex(oldIndex, data))
      }
    }

    override def query(criteria: IndexSelection) = {
      val get = values.lift
      val indicesIterator = criteria match {
        case IndexRange(from, to) => (from to to).iterator
        case SpecificIndices(indices) => indices.iterator
      }

      val found = indicesIterator.flatMap { idx =>
        if (idx > Int.MaxValue | idx < 0) {
          None
        } else {
          get(idx.toInt).map { entry =>
            IndexQueryResult[K, T](entry.seqNo, entry.key, idx, entry.value)
          }
        }
      }
      Publishers(found)
    }
  }

}