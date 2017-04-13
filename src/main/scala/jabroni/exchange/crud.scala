package jabroni.exchange

import scala.concurrent.Future

/**
  * Can CR(u)D the T
  *
  * @tparam T
  */
trait Crud[K, T] extends Create[K, T] with Read[K, T] with Update[K, T] with Delete[K]

trait Update[K, T] {
  def update(id: K, newValue: T): Future[Boolean]
}

trait Delete[K] {
  def delete(id: K): Future[Boolean]
}

trait Read[K, T] {
  def read(id: K): Future[Option[T]]
}

trait Create[K, T] {
  def save(id: K, value: T): Future[K]
}