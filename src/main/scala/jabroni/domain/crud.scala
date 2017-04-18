package jabroni.domain

import scala.concurrent.Future

/**
  * Can CR(u)D the T
  *
  * @tparam T
  */
trait Crud[K, T] extends Create[K, T] with Read[K, T] with Delete[K] //with Update[K, T]

object Crud {

  sealed trait CrudRequest

  sealed trait CrudResponse


  case class CreateRequest[K, T](id: K, newValue: T) extends CrudRequest

  case class CreateResponse[K](id: K, updated: Boolean) extends CrudResponse

  case class GetRequest[K](id: K) extends CrudRequest

  case class GetResponse[K, T](id: K, value: T) extends CrudResponse

  case class DeleteRequest[K](id: K) extends CrudRequest

  case class DeleteResponse[K](id: K, deleted: Boolean) extends CrudResponse

  //  case class UpdateRequest[K, T](id: K, newValue: T) extends CrudRequest
  //  case class UpdateResponse(updated : Boolean) extends CrudResponse

}

trait Create[K, T] {
  def save(id: K, value: T): Future[K]
}

trait Read[K, T] {
  def read(id: K): Future[Option[T]]
}

//trait Update[K, T] {
//  def update(id: K, newValue: T): Future[Boolean]
//}
trait Delete[K] {
  def delete(id: K): Future[Boolean]
}

