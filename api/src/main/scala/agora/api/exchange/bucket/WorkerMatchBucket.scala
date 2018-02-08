package agora.api.exchange.bucket

import agora.api.json.JPath
import io.circe.Json

/**
  * A 'WorkerMatchBucket' groups worker subscriptions and/or jobs into 'buckets' primarily as a performance measure
  * so as not to have to consider all subscriptions/jobs on each update.
  *
  * For systems which regularly update their subscription data, or have relatively large number of work subscriptions
  * or jobs which may remain queued, we reduce the number of candidates to consider within the exchange by grouping
  * them into 'buckets', which are simply maps based on the values at the provided keys.
  *
  * This should reduce checking subscribers from an O(n) operation to potentially constant time (depending on how
  * many subscribers 'fit' into the bucket).
  *
  * Buckets are created when a job specifies a non-empty WorkerMatchBucket. When a job is received w/ a non-empty
  * bucket, existing subscriptions are grouped by evaluating their JobBucket criteria.
  *
  * The submitted (and subsequent) jobs will only consider subscribers in the relevant bucket.
  *
  * New subscriptions created will be sorted into all existing buckets.
  */
case class WorkerMatchBucket(buckets: List[JobBucket]) {
  def isEmpty = buckets.isEmpty

  def keys: BucketPathKey = buckets.map(_.key)

  def valueKeys: List[Json] = buckets.map(_.value)
}

object WorkerMatchBucket {

  def apply(buckets: (JPath, Json)*) = {
    val list = buckets.map {
      case (path, json) => JobBucket(BucketKey(path, false), json)
    }
    new WorkerMatchBucket(list.toList)
  }

  val Empty = WorkerMatchBucket(Nil)
}
