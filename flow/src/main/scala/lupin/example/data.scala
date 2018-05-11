package lupin.example

import lupin.data.Accessor

trait IndexedField[T] {
  def forSelection(selection: IndexSelection): Map[Long, T]
}

object DataOperation extends Enumeration {
  val Delete, Update, Create = Value
}

case class Property[T](name: String, ordering: Ordering[T], accessor: Accessor.Aux[T])

case class ColumnView[T](columns: List[Property[T]]) {
  def cellNames = columns.map(_.name)
}

case class SortCriteria(property: String, ascending: Boolean = true)

/**
  * Represents a current vew (rows, and columns, sort criteria) onto some data of type T
  */
case class ViewPort(indices: IndexSelection, sortColumn: SortCriteria, cols: List[String])

object ViewPort {

  def apply(fromIndex: Long, toIndex: Long, sortColumn: SortCriteria, cols: List[String] = Nil) = {
    new ViewPort(IndexSelection(fromIndex, toIndex), sortColumn, cols)
  }
  def apply(indices: List[Long], sortColumn: SortCriteria, cols: List[String]) = {
    new ViewPort(IndexSelection(indices), sortColumn, cols)
  }
}
