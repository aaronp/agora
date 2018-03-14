package agora.json

import cats.kernel.Semigroup
import io.circe.Json

/** This instance is intentionally NOT implicit. It is just defined so that, where appropriate,
  * it can be used for semigroup operations
  */
object JsonSemigroup extends Semigroup[Json] {
  override def combine(x: Json, y: Json): Json = deepMergeWithArrayConcat(x, y)
}
