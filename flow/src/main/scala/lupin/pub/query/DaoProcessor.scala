package lupin.pub.query

import lupin.Publishers
import lupin.example.Accessor
import lupin.pub.query.DaoProcessor.CrudOperation
import lupin.pub.sequenced.DurableProcessor

import scala.concurrent.ExecutionContext

trait DaoProcessor[K, T] extends QueryPublisher[Option[K], CrudOperation[K, T]]

object DaoProcessor {

  sealed trait CrudOperation[K, T]

  case class Create[K, T](key: K, value: T) extends CrudOperation[K, T]

  case class Update[K, T](key: K, value: T) extends CrudOperation[K, T]

  case class Delete[K, T](key: K) extends CrudOperation[K, T]

  def apply[K, T](implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext) = {
    val dp = DurableProcessor[T]()
    var valuesById: Map[K, T] = Map[K, T]()

    Publishers.map(dp) { value =>
      val key = accessor.get(value)

      valuesById = valuesById.updated(key, value)
      valuesById.get(key) match {
        case None => Create[K, T](key, value)
        case Some(_) => Update[K, T](key, value)
      }
    }
  }
}
