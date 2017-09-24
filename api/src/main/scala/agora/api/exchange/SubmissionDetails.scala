package agora.api.exchange

import agora.api.User
import agora.api.json.{JMatcher, JsonAppendable}
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
case class SubmissionDetails(override val aboutMe: Json, selection: SelectionMode, awaitMatch: Boolean, workMatcher: JMatcher, orElse: List[JMatcher]) extends JsonAppendable {
  def matchingPath(path: String): SubmissionDetails = {
    import agora.api.Implicits._
    copy(workMatcher = workMatcher.and("path" === path))
  }

  def withMatcher(newMatcher: JMatcher) = copy(workMatcher = newMatcher)

  def andMatching(andCriteria: JMatcher) = copy(workMatcher = workMatcher.and(andCriteria))
  def orMatching(orCriteria: JMatcher)   = copy(workMatcher = workMatcher.or(orCriteria))

  def submittedBy: User = SubmissionDetails.submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }

  /**
    * If 'orElse' lists another work subscription, then a [[SubmissionDetails]] is returned using that
    * as the work matcher with the remaining 'orElse' tail as it's 'orElse'
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
    copy(aboutMe = aboutMe.deepMerge(qualified))
  }

}

object SubmissionDetails {

  def submissionUser = JsonPath.root.submissionUser.string

  def apply( // format:off
            submittedBy: User = Properties.userName,
            matchMode: SelectionMode = SelectionFirst(),
            awaitMatch: Boolean = true,
            workMatcher: JMatcher = JMatcher.matchAll,
            orElse: List[JMatcher] = Nil
            // format:on
  ) = {
    val json = Json.obj("submissionUser" -> Json.fromString(submittedBy))
    new SubmissionDetails(json, matchMode, awaitMatch, workMatcher, orElse)
  }
}
