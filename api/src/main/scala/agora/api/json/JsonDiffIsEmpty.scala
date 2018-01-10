package agora.api.json

import agora.io.core.IsEmpty

object JsonDiffIsEmpty extends IsEmpty[JsonDiff] {
  override def isEmpty(diff: JsonDiff): Boolean = {
    diff.deltas.isEmpty
  }
}
