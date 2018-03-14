package agora.json

import agora.core.DataDiff
import io.circe.Json

object StrippedJsonDiff extends DataDiff[Json, Json] {
  override def diff(lhs: Json, rhs: Json): Json = {
    JsonDiff(lhs, rhs).strip(rhs)
  }
}
