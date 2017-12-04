package agora.api.exchange.bucket

import io.circe.Json

/**
  * Referenced by submitted jobs
  * @param key the bucket key (path to the work subscription value)
  * @param value a the key value a worker should have
  */
case class JobBucket(key: BucketKey, value: Json)
