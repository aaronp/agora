package agora.api.streams

import agora.api.json.JsonDiff
import io.circe.Json

/**
  * A 'DataDiff' can diff two instances of A and produce a delta of type D
  *
  * @tparam A
  * @tparam D
  */
trait DataDiff[A, D] {

  def diff(a: A, b: A): D
}

object DataDiff {
  implicit object JsonDiffAsDataDiff extends DataDiff[Json, JsonDiff] {
    override def diff(a: Json, b: Json): JsonDiff = JsonDiff(a, b)
  }
}
