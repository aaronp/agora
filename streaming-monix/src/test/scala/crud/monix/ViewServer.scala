package crud.monix
import monix.reactive.Observable

case class ViewPort(fromRow: Int, toRow: Int, cols: List[String])

sealed trait CrudOperation
object CrudOperation {
  final case object Create extends CrudOperation
  final case object Update extends CrudOperation
  final case object Delete extends CrudOperation
}
case class DataUpdate[A](op : CrudOperation, value: A)

object ViewPort {
  def apply[A : Ordering](views: Observable[ViewPort], data: Observable[A]) = {
    //views.withLatestFrom()
    ???
  }
}

object example {}
