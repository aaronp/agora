package lupin.pub.query

import lupin.data.Accessor
import lupin.example.IndexSelection
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

trait IndexQuerySource[K, T] {
  def query(criteria: IndexSelection): Publisher[IndexQueryResult[K, T]]
}

object IndexQuerySource {


  /**
    * An example of how to index a property 'A' of 'T' which is keyed on 'K'
    *
    * @param data
    * @param inputDao
    * @param getter
    * @param getId
    * @param execContext
    * @tparam K
    * @tparam T
    * @tparam A
    * @return
    */
  def apply[K, T, A: Ordering](data: Publisher[(Long, T)],
                               inputDao: SyncDao[K, (Long, T)] = null)(getter: T => A)(implicit getId: Accessor[(Long, T), K], execContext: ExecutionContext): Publisher[IndexedValue[K, A]] = {
    val insert: Publisher[(CrudOperation[K], (Long, T))] = Indexer.crud(data, inputDao)
    forSequencedDataFeed(insert, inputDao)(getter)
  }

  def forSequencedDataFeed[K, T, A: Ordering](sequencedDataFeed: Publisher[(CrudOperation[K], (Long, T))],
                                              inputDao: SyncDao[K, (Long, T)] = null)(getter: T => A)(implicit execContext: ExecutionContext): Publisher[IndexedValue[K, A]] = {
    import lupin.implicits._
    val sequencedValues: Publisher[Sequenced[(CrudOperation[K], A)]] = sequencedDataFeed.map {
      case (crudOp, (seqNo, value)) => Sequenced(seqNo, (crudOp, getter(value)))
    }

    val indexer: IndexQuerySource[K, A] with Indexer[K, A] = IndexQuerySource.apply(Indexer.slowInMemoryIndexer)
    Indexer[K, A](sequencedValues, indexer)
  }


  /**
    * Some instances of indexer will be immutable as they fold over values. This instance will wrap those so as
    * to provide access to the 'latest' indexer
    *
    * @param initialValue
    * @tparam K
    * @tparam T
    * @return
    */
  def apply[K, T](initialValue: Indexer.QueryIndexer[K, T]): IndexQuerySource[K, T] with Indexer[K, T] = {
    object Impl extends IndexQuerySource[K, T] with Indexer[K, T] {
      @volatile var current: IndexQuerySource[K, T] with Indexer[K, T] = initialValue

      override def query(criteria: IndexSelection) = {
        val snapshot = current
        snapshot.query(criteria)
      }

      override def index(seqNo: Long, data: T, op: CrudOperation[K]) = {
        val (newInst, indexedValue) = current.index(seqNo, data, op)
        val casted = newInst.asInstanceOf[IndexQuerySource[K, T] with Indexer[K, T]]
        //        val casted:  = ev(newInst)
        current = casted
        (this, indexedValue)
      }
    }

    Impl
  }
}
