package agora.rest.stream

import scala.concurrent.Future

case class ColumnSort(columns: String, ascending: Boolean)

case class SortCriteria(columns: List[ColumnSort] = Nil)

case class ViewPortCriteria[C](firstRowIndex: Int, numberOfRows: Int, columns: List[String], sort: SortCriteria, filter: C)

trait TabularDataSource[CRITERIA, DELTAS] {

  def deltas(query: ViewPortCriteria[CRITERIA]): Future[DELTAS]
}
