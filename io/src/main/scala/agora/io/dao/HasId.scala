package agora.io.dao

/**
  * @tparam T the type for which there's an ID
  */
trait HasId[T] {
  def id(value: T): String
}

object HasId {
  def instance[T](f: T => String) = new HasId[T] {
    override def id(value: T) = f(value)
  }
}
