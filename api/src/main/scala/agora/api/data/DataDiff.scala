package agora.api.data

import agora.api.json.JsonDiff
import io.circe.Json

/**
  * A 'DataDiff' can diff two instances of A and produce a delta of type D
  *
  * @tparam A the data type to diff
  * @tparam D the delta type
  */
trait DataDiff[A, D] {

  /**
    * Produce a difference D from two instances of A
    *
    * @param lhs the left-hand-side
    * @param rhs the right-hand-side
    * @return a difference D
    */
  def diff(lhs: A, rhs: A): D

  def map[D1](f: D => D1): DataDiff[A, D1] = {
    val self = this
    new DataDiff[A, D1] {
      override def diff(lhs: A, rhs: A) = f(self.diff(lhs, rhs))
    }
  }
}

object DataDiff {

  import io.circe.generic.auto._
  import io.circe.syntax._

  object JsonDiffWithValues extends DataDiff[Json, (Json, Json, JsonDiff)] {
    override def diff(lhs: Json, rhs: Json) = (lhs, rhs, JsonDiff(lhs, rhs))
  }
  implicit object IsTupleEmpty extends IsEmpty[(Json, Json, JsonDiff)] {
    override def isEmpty(value: (Json, Json, JsonDiff)): Boolean = IsEmpty.JsonDiffIsEmpty.isEmpty(value._3)
  }

  object JsonDiffAsDataDiff extends DataDiff[Json, JsonDiff] {
    override def diff(lhs: Json, rhs: Json): JsonDiff = JsonDiff(lhs, rhs)
  }

  val JsonDiffAsDeltas = JsonDiffAsDataDiff.map { jsonDiff =>
    jsonDiff.asJson
  }

  object StrippedJsonDiff extends DataDiff[Json, Json] {
    override def diff(lhs: Json, rhs: Json): Json = {
      JsonDiff(lhs, rhs).strip(rhs)
    }
  }

}
