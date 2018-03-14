package agora.core

trait IsEmpty[T] {
  def isEmpty(value: T): Boolean
}

object IsEmpty {
  class IsEmptyOps[T: IsEmpty](val value: T) {
    def isEmpty  = implicitly[IsEmpty[T]].isEmpty(value)
    def nonEmpty = !implicitly[IsEmpty[T]].isEmpty(value)
  }

  implicit def asIsEmptyOps[T: IsEmpty](value: T) = new IsEmptyOps[T](value)

  implicit object SeqIsEmpty extends IsEmpty[TraversableOnce[_]] {
    override def isEmpty(value: TraversableOnce[_]): Boolean = value.isEmpty
  }
}
