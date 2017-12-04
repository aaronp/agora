package agora.api.exchange.bucket

import io.circe.Json

/**
  * A 'WorkerMatchBucket' groups worker subscriptions and/or jobs into 'buckets' primarily as a performance measure
  * so as not to have to consider all subscriptions/jobs on each update.
  *
  * For systems which regularly update their subscription data, or have relatively large number of work subscriptions
  * or jobs which may remain queued, we reduce the number of candidates to consider within the exchange by grouping
  * them into 'buckets', which are simply maps based on the values at the provided keys.
  *
  */
case class WorkerMatchBucket(buckets: List[JobBucket]) {
  def isEmpty               = buckets.isEmpty
  def keys: BucketPathKey   = buckets.map(_.key)
  def valueKeys: List[Json] = buckets.map(_.value)
}

object WorkerMatchBucket {

  val Empty = WorkerMatchBucket(Nil)
}
