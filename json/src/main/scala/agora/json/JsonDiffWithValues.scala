package agora.json

import agora.core.DataDiff
import io.circe.Json

object JsonDiffWithValues extends DataDiff[Json, (Json, Json, JsonDiff)] {
  override def diff(lhs: Json, rhs: Json) = (lhs, rhs, JsonDiff(lhs, rhs))
}
