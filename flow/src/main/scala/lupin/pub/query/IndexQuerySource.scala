package lupin.pub.query

import lupin.data.Accessor
import lupin.example.IndexSelection
import lupin.pub.sequenced.SequencedProcessor
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * Represents something which, given an [[IndexSelection]] it can return a publisher for the
  * entries at the given range.
  *
  * As each entry contains a 'seqNo' field, it should be possible to subscribe for updates from
  * that sequence in order to get a full picture of the state of a value and its subsequent updates.
  *
  * Some implementations may provide something like 'Timestamp => IndexQuerySource' in order to
  * expose queries which return values at a particular time, the [[IndexedEntry]] results of which
  * can sill be used in the same way to observe updates from a historic time
  *
  * @tparam K
  * @tparam T
  */
trait IndexQuerySource[K, T] {

  /** @param criteria the indices to return values for
    * @return the entries for a particular index
    */
  def query(criteria: IndexSelection): Publisher[IndexedEntry[K, T]]

  final def between(fromIndex: Long, toIndex: Long): Publisher[IndexedEntry[K, T]] = query(IndexSelection(fromIndex, toIndex))

  final def forIndices(indices: List[Long]): Publisher[IndexedEntry[K, T]] = query(IndexSelection(indices))

  final def forIndices(firstIndex: Long, theRest: Long*): Publisher[IndexedEntry[K, T]] = {
    forIndices(firstIndex :: theRest.toList)
  }
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
  def apply[K, T, A: Ordering](data: Publisher[(Long, T)], inputDao: SyncDao[K, T] = null)(
    getter: T => A)(implicit getId: Accessor[T, K], execContext: ExecutionContext): Publisher[IndexedValue[K, A]] with IndexQuerySource[K, A] = {
    val insert = Indexer.crud[K, T](data, inputDao)

    val mapped: Publisher[Sequenced[(CrudOperation[K], A)]] = Sequenced.map(insert)(getter)
    fromSequencedUpdates(mapped)
  }


  /**
    *
    * @param sequencedUpdates
    * @param execContext
    * @tparam K
    * @tparam T
    * @return an IndexQuerySource which can be used to read the data sent through it when used as an [[Indexer]]
    */
  def fromSequencedUpdates[K, T: Ordering](sequencedUpdates: Publisher[Sequenced[(CrudOperation[K], T)]])(implicit execContext: ExecutionContext): Publisher[IndexedValue[K, T]] with IndexQuerySource[K, T] = {
    val withLatest = keepLatest[K, T](Indexer.slowInMemoryIndexer)

    val indexer = Indexer(sequencedUpdates, withLatest)

    new Publisher[IndexedValue[K, T]] with IndexQuerySource[K, T] {
      override def subscribe(s: Subscriber[_ >: IndexedValue[K, T]]): Unit = {
        indexer.subscribe(s)
      }

      override def query(criteria: IndexSelection): Publisher[IndexedEntry[K, T]] = {
        val snapshotResults = withLatest.query(criteria)


        var minSeqNoFound = -1L
        ???
        //        snapshotResults.map { res =>
        //
        //      }
        snapshotResults
      }
    }
  }

  /**
    * Some instances of indexer will be immutable as they fold over values. This function will provide an instance
    * to wrap an underlying indexer in order to keep the 'latest' updated value.
    *
    * It would be equivalent to something like:
    *
    * {{{
    *
    *
    *
    *   val stateUpdate : (T, T) => T
    *
    *   val latestState = keepLatest(stateUpdate)
    *
    *   // this would be done asynchronously elsewhere. While it's working, our 'latestState'
    *   // can be used to get the most recent result from the wrapped 'stateUpdate'
    *   someValues.foldLeft(latestState)(latestState)
    *
    * }}}
    *
    * @param initialValue
    * @tparam K
    * @tparam T
    * @return
    */
  def keepLatest[K, T](initialValue: Indexer.QueryIndexer[K, T]): IndexQuerySource[K, T] with Indexer[K, T] = {
    object Impl extends IndexQuerySource[K, T] with Indexer[K, T] {
      @volatile var current: IndexQuerySource[K, T] with Indexer[K, T] = initialValue

      override def query(criteria: IndexSelection) = {
        val snapshot = current
        snapshot.query(criteria)
      }

      override def index(seqNo: Long, data: T, op: CrudOperation[K]) = {
        val (newInst, indexedValue) = current.index(seqNo, data, op)
        val casted = newInst.asInstanceOf[IndexQuerySource[K, T] with Indexer[K, T]]
        current = casted
        (this, indexedValue)
      }
    }

    Impl
  }
}
