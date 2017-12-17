package agora.api.exchange

import agora.api.Implicits._
import agora.api.config.JsonConfig
import agora.api.exchange.bucket.{JobBucket, WorkerMatchBucket}
import agora.api.json.JPredicate
import agora.config.implicits._
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.parser._

/**
  * Contains details pertaining to a [[SubmitJob]] matching a [[WorkSubscription]].
  *
  * @param criteria     the match criteria to evaluate against each worker's [[agora.api.worker.WorkerDetails]]
  * @param workerBucket a performance measure - a means to group work submissions into 'buckets' based on data obtained by some [[agora.api.json.JPath]]s so that we only need to evaluate eligible buckets instead of *all* work subscriptions
  * @param onMatchUpdate an action used to update the [[agora.api.worker.WorkerDetails]] of matched workers
  */
case class WorkMatcher(criteria: JPredicate, workerBucket: WorkerMatchBucket = WorkerMatchBucket.Empty, onMatchUpdate: List[OnMatchUpdateAction] = Nil) {

  def matchingPath(path: String) = copy(criteria = criteria.and("path" === path))

  def withCriteria(newCriteria: JPredicate) = copy(criteria = newCriteria)

  def andMatching(andCriteria: JPredicate) = copy(criteria = criteria.and(andCriteria))

  def orMatching(orCriteria: JPredicate) = copy(criteria = criteria.or(orCriteria))

  def withBucket(bucket: WorkerMatchBucket) = copy(workerBucket = bucket)
}

object WorkMatcher {
  def fromConfig(config: Config): WorkMatcher = {
    import JsonConfig.implicits._

    val criteria = config.as[JPredicate]("criteria")

    val bucketList = {
      val justBuckets = config.withOnlyPath("buckets")
      val parsedJson  = parse(justBuckets.json)
      val bucketsArray = parsedJson.flatMap { confJson =>
        val bucketsObj = confJson.hcursor.downField("buckets")
        bucketsObj.as[List[JobBucket]]
      }
      bucketsArray.right.getOrElse(sys.error(s"Couldn't parse work matcher: $parsedJson"))
    }

    new WorkMatcher(criteria, WorkerMatchBucket(bucketList))

  }
}
