package agora.api.streams

import io.circe.Json

trait IsEmpty[T] {
  def isEmpty(value: T): Boolean
}

object IsEmpty {
  class IsEmptyOps[T: IsEmpty](val value: T) {
    def isEmpty = implicitly[IsEmpty[T]].isEmpty(value)
  }

  implicit def asIsEmptyOps[T: IsEmpty](value: T) = new IsEmptyOps[T](value)

  implicit object SeqIsEmpty extends IsEmpty[TraversableOnce[_]] {
    override def isEmpty(value: TraversableOnce[_]): Boolean = value.isEmpty
  }
  implicit object JsonIsEmpty extends IsEmpty[Json] {
    override def isEmpty(value: Json): Boolean = {
      value.isNull || value.hcursor.downField("deltas").values.exists(_.isEmpty)
    }
  }
}
