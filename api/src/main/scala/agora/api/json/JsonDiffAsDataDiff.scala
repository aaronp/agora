package agora.api.json

import agora.io.core.DataDiff
import io.circe.Json

object JsonDiffAsDataDiff extends DataDiff[Json, JsonDiff] {
  override def diff(lhs: Json, rhs: Json): JsonDiff = JsonDiff(lhs, rhs)
}
