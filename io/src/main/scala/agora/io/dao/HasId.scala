package agora.io.dao

import scala.reflect.ClassTag

/**
  * @tparam T the type for which there's an ID
  */
trait HasId[T] {
  def id(value: T): String
}

object HasId {
  def instance[T: HasId]: HasId[T] = implicitly[HasId[T]]

  trait LowPriorityHasIdImplicits {
    implicit def hasIdIdentity: HasId[String] = HasId.identity
  }
  object implicits extends LowPriorityHasIdImplicits

  case object identity extends HasId[String] {
    override def id(value: String): String = value
  }

  def lift[T](f: T => String) = new HasId[T] {
    override def id(value: T) = f(value)
  }
}
// TODO -= make the ID type generic:
//
//trait HasId[T] {
//  type Id
//  def id(value: T): Id
//}
//
//object HasId {
//
//  type Aux[T, K] = HasId[T] { type Id = K }
//  def instance[T: Aux]: HasId[T] = implicitly[HasId[T]]
//
//  trait LowPriorityHasIdImplicits {
//    implicit def hasIdIdentity: HasId[String] = HasId.identity
//  }
//  object implicits extends LowPriorityHasIdImplicits
//
//  case object identity extends HasId[String] {
//    override def id(value: String): String = value
//  }
//
//  def lift[T](f: T => String) = new HasId[T] {
//    override def id(value: T) = f(value)
//  }
//}
