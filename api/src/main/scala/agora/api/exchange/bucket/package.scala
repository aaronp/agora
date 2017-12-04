package agora.api.exchange

import io.circe.Json

package object bucket {
  type BucketPathKey  = List[BucketKey]
  type BucketValueKey = List[Json]
}
