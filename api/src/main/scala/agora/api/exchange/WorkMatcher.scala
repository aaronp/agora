package agora.api.exchange

import agora.api.Implicits._
import agora.api.config.JsonConfig
import agora.api.exchange.bucket.{JobBucket, WorkerMatchBucket}
import agora.api.json.JPredicate
import agora.config.implicits._
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.parser._

case class WorkMatcher(criteria: JPredicate, workerBucket: WorkerMatchBucket = WorkerMatchBucket.Empty) {

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
