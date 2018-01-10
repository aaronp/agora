package agora.api.json

import agora.io.core.IsEmpty
import io.circe.Json

object JsonIsEmpty extends IsEmpty[Json] {
  override def isEmpty(value: Json): Boolean = {
    value.isNull || !value.hcursor.downField("deltas").values.exists(_.nonEmpty)
  }
}
