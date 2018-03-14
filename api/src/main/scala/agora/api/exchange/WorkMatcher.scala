package agora.api.exchange

import agora.api.Implicits._
import agora.api.config.JsonConfig
import agora.api.exchange.bucket.{JobBucket, WorkerMatchBucket}
import agora.json.JPredicate
import agora.config.implicits._
import com.typesafe.config.Config
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._

/**
  * Contains details pertaining to a [[SubmitJob]] matching a [[WorkSubscription]].
  *
  * Both the 'criteria' and 'workerBucket' are concerned with matching a ob again
  *
  * @param criteria     the match criteria to evaluate against each worker's [[agora.api.worker.WorkerDetails]]
  * @param workerBucket a performance measure - a means to group work submissions into 'buckets' based on data obtained
  *                     by some [[agora.json.JPath]]s so that we only need to evaluate eligible buckets instead of
  *                     *all* work subscriptions. This is mostly useful for when we have a large number of subscriptions.
  *                     Instead of O(n) performance by evaluating _all_ subscribers to see if they match, only subscribers
  *                     which are grouped into the specified bucket are considered.
  *                     Buckets are created when a job is received which specifies a 'workerBucket'. Both existing workers
  *                     and newly workers will then be evaluated by the JPaths specified by the bucket and grouped by
  *                     their values.
  * @param onMatchUpdate update actions used to update the [[agora.api.worker.WorkerDetails]] of matched workers.
  *                      Useful for e.g. creating sticky sessions or anytime when you want to ensure certain matched
  *                      criteria always gets routed to a single worker.
  */
case class WorkMatcher(criteria: JPredicate,
                       workerBucket: WorkerMatchBucket = WorkerMatchBucket.Empty,
                       onMatchUpdate: Vector[OnMatchUpdateAction] = Vector.empty)
    extends HasWorkMatcher {

  override type Me = WorkMatcher

  def matchingPath(path: String) = copy(criteria = criteria.and("path" === path))

  def withCriteria(newCriteria: JPredicate) = copy(criteria = newCriteria)

  def andMatching(andCriteria: JPredicate) = copy(criteria = criteria.and(andCriteria))

  def orMatching(orCriteria: JPredicate) = copy(criteria = criteria.or(orCriteria))

  def withBucket(bucket: WorkerMatchBucket) = copy(workerBucket = bucket)

  def addUpdateAction(action: OnMatchUpdateAction) = copy(onMatchUpdate = onMatchUpdate :+ action)
}

object WorkMatcher {

  implicit object Format extends Encoder[WorkMatcher] with Decoder[WorkMatcher] {
    override def apply(wm: WorkMatcher): Json = {
      import io.circe.generic.auto._
      import io.circe.syntax._
      Json.obj(
        "criteria" -> wm.criteria.json,
        "buckets"  -> wm.workerBucket.buckets.asJson,
        "updates"  -> wm.onMatchUpdate.asJson
      )
    }

    override def apply(c: HCursor): Result[WorkMatcher] = {
      def bucketOpt = {
        val optBuckets: ACursor = c.downField("buckets")
        if (optBuckets.succeeded) {
          optBuckets.as[List[JobBucket]].right.map(WorkerMatchBucket.apply)
        } else {
          Right(WorkerMatchBucket())
        }
      }

      def updatesOpt = {
        val optUpdates: ACursor = c.downField("updates")
        if (optUpdates.succeeded) {
          optUpdates.as[Vector[OnMatchUpdateAction]]
        } else {
          Right(Vector[OnMatchUpdateAction]())
        }
      }

      for {
        pred    <- c.downField("criteria").as[JPredicate].right
        bucket  <- bucketOpt.right
        updates <- updatesOpt.right
      } yield {
        WorkMatcher(pred, bucket, updates)
      }
    }
  }

  def fromConfig(config: Config): WorkMatcher = {
    import JsonConfig.implicits._

    val criteria = config.as[JPredicate]("criteria")

    val bucketList: List[JobBucket] = {
      val justBuckets = config.withOnlyPath("buckets")
      val parsedJson  = parse(justBuckets.json)
      val bucketsArray = parsedJson.right.flatMap { confJson =>
        val bucketsObj = confJson.hcursor.downField("buckets")
        bucketsObj.as[List[JobBucket]]
      }
      bucketsArray.right.getOrElse(sys.error(s"Couldn't parse work matcher: $parsedJson"))
    }
    val updates = {
      val justUpdates = config.withOnlyPath("updates")
      val parsedJson  = parse(justUpdates.json)
      val updatesArray = parsedJson.right.flatMap { confJson =>
        val updatesObj = confJson.hcursor.downField("updates")
        updatesObj.as[Vector[OnMatchUpdateAction]]
      }
      updatesArray.right.getOrElse(Vector[OnMatchUpdateAction]())
    }

    new WorkMatcher(criteria, WorkerMatchBucket(bucketList), updates)

  }
}
