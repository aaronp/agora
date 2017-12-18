package agora.api.exchange

import agora.api.User
import agora.api.exchange.bucket.{BucketKey, JobBucket, WorkerMatchBucket}
import agora.api.json.{JExpression, JPath, JPredicate, JsonAppendable}
import io.circe._
import io.circe.optics.JsonPath

import scala.util.Properties

/** The SubmissionDetails is the ansillary information submitted with a job request.
  *
  * Where a typical REST endpoint would just take some POSTed json request, the [[SubmitJob]] requests wraps that
  * json together will some information intended for the [[Exchange]] in order for it to match the job with a worker.
  *
  * The SubmissionDetails is that additional information to act as instructions/information specific to the job scheduling/matching.
  * It contains:
  *
  * @param aboutMe     a hold-all json blob which can include any details a client request wishes to expose to potential workers.
  *                    It could contain e.g. session information, the submitting or 'run-as' user, etc.
  * @param selection   an instruction on which/how to select matching work subscriptions as an opportunity to filter on best-fit
  * @param awaitMatch  if true, the job submission to the exchange should block until one or more matching worker(s) is found
  * @param workMatcher the json criteria used to match work subscriptions
  * @param orElse      should the 'workMatcher' not match _any_ current work subscriptions, the [[SubmitJob]] is resubmitted with the 'orElse' criteria
  */
// format: off
case class SubmissionDetails(
                              override val aboutMe: Json,
                              selection: SelectionMode,
                              awaitMatch: Boolean,
                              workMatcher: WorkMatcher,
                              orElse: List[WorkMatcher])
  extends JsonAppendable
with HasWorkMatcher {

  override type Me = SubmissionDetails
  // format: on

  def matchingPath(path: String): SubmissionDetails = copy(workMatcher = workMatcher.matchingPath(path))

  def withSelection(mode: SelectionMode) = copy(selection = mode)

  def withBucket(bucket: WorkerMatchBucket): SubmissionDetails = copy(workMatcher = workMatcher.withBucket(bucket))

  def withJobBuckets(buckets: JobBucket*): SubmissionDetails = {
    withBucket(WorkerMatchBucket(buckets.toList))
  }

  def withBuckets(buckets: (JPath, Json)*): SubmissionDetails = {
    val jobBuckets = buckets.map {
      case (path, json) => JobBucket(BucketKey(path, false), json)
    }
    withJobBuckets(jobBuckets: _*)
  }

  def orElseMatch(other: JPredicate): SubmissionDetails = orElseMatch(WorkMatcher(other))

  def orElseMatch(other: WorkMatcher): SubmissionDetails = copy(orElse = orElse :+ other)

  def withMatchCriteria(newMatcher: JPredicate) = copy(workMatcher = workMatcher.withCriteria(newMatcher))

  def withMatcher(newMatcher: WorkMatcher) = copy(workMatcher = newMatcher)

  def andMatching(andCriteria: JPredicate) = copy(workMatcher = workMatcher.andMatching(andCriteria))

  def orMatching(orCriteria: JPredicate) = copy(workMatcher = workMatcher.orMatching(orCriteria))

  /** @return the user who submitted this job
    */
  def submittedBy: User = SubmissionDetails.submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }

  /**
    * If 'orElse' lists another work subscription, then a [[SubmissionDetails]] is returned using that
    * as the work matcher with the remaining 'orElse' tail as it's 'orElse'
    *
    * @return a [[SubmissionDetails]] referring to the orElse list if it's non-empty
    */
  private[exchange] def next(): Option[SubmissionDetails] = orElse match {
    case Nil          => None
    case head :: tail => Option(copy(workMatcher = head, orElse = tail))
  }

  def +[T: Encoder](keyValue: (String, T)): SubmissionDetails = add(keyValue)

  def add[T: Encoder](keyValue: (String, T)): SubmissionDetails = {
    val (key, value) = keyValue
    withData(value, key)
  }

  def withData[T: Encoder](data: T, name: String = null): SubmissionDetails = {
    val json: Json = implicitly[Encoder[T]].apply(data)
    val qualified  = Json.obj(namespace(data.getClass, name) -> json)
    append(qualified)
  }

  def append(json: Json): SubmissionDetails = copy(aboutMe = aboutMe.deepMerge(json))

  override def addUpdateAction(action: OnMatchUpdateAction) = {
    copy(workMatcher = workMatcher.addUpdateAction(action))
  }
}

object SubmissionDetails {

  def submissionUser = JsonPath.root.submissionUser.string

  def apply(submissionUser: User = Properties.userName,
            matchMode: SelectionMode = SelectionOne,
            awaitMatch: Boolean = true,
            workMatcher: WorkMatcher = WorkMatcher(JPredicate.matchAll),
            orElse: List[WorkMatcher] = Nil) = {
    val json = Json.obj("submissionUser" -> Json.fromString(submissionUser))
    new SubmissionDetails(json, matchMode, awaitMatch, workMatcher, orElse)
  }
}
