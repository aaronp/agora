package lupin.example

import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

object CellUpdate {
  def subscribeTo[T, ID, U <: FieldUpdate[ID]](data: Publisher[T], views: Publisher[ViewPort])(implicit ec: ExecutionContext): CellFeed[ID, U] = {
//    val joined: Publisher[TupleUpdate[T, ViewPort]] = Publishers.join(data, views)
//
//    val first: CellUpdate[ID, U] = CellUpdate(Map.empty)
//    Publishers.foldLeft(joined, first) {
//      case (cellUpdate, LeftUpdate(value))            => cellUpdate
//      case (cellUpdate, RightUpdate(viewPort))        => cellUpdate
//      case (cellUpdate, BothUpdated(value, viewPort)) => cellUpdate
//    }

    ???
  }
}

/** Represents a tabular update (cell coords -> new value)
  *
  * @param updates
  * @tparam ID
  * @tparam U
  */
case class CellUpdate[ID, U <: FieldUpdate[ID]](updates: Map[CellCoord, U]) {

  /**
    * update this 'CellUpdate' w/ the given (coors,value) pairs
    *
    * @param pairs
    * @return
    */
  def updated(pairs: (CellCoord, U)*): CellUpdate[ID, U] = {
    val newMap = pairs.foldLeft(updates) {
      case (map, (key, value)) => map.updated(key, value)
    }
    copy(updates = newMap)
  }

  def indices: Option[Set[SeqNo]] = {
    if (updates.isEmpty) {
      None
    } else {
      Option(updates.keySet.map(_.index))
    }
  }

  def minIndex = indices.map(_.min)

  def maxIndex = indices.map(_.max)

  def filter(predicate: CellCoord => Boolean): CellUpdate[ID, U] = {
    copy(updates = updates.filterKeys(predicate))
  }

  def merge(update: CellUpdate[ID, U]): CellUpdate[ID, U] = {
    val newUpdates = updates ++ update.updates
    CellUpdate(newUpdates)
  }
}
