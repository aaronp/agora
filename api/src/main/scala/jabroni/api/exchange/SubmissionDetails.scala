package jabroni.api.exchange

import io.circe._
import io.circe.optics.JsonPath
import jabroni.api.User
import jabroni.api.json.{JMatcher, JsonAppendable}

import scala.util.Properties

/**
  * Contains instructions/information specific to the job scheduling/matching
  */
case class SubmissionDetails(override val aboutMe: Json,
                             selection: SelectionMode,
                             workMatcher: JMatcher,
                             awaitMatch : Boolean) extends JsonAppendable {

  def submittedBy: User = SubmissionDetails.submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }

  def +[T: Encoder](keyValue: (String, T)): SubmissionDetails = add(keyValue)
  def add[T: Encoder](keyValue: (String, T)): SubmissionDetails = {
    val (key, value) = keyValue
    withData(value, key)
  }

  def withData[T: Encoder](data: T, name: String = null): SubmissionDetails = {
    val json: Json = implicitly[Encoder[T]].apply(data)
    val qualified = Json.obj(namespace(data.getClass, name) -> json)
    copy(aboutMe = aboutMe.deepMerge(qualified))
  }

}


object SubmissionDetails {

  def submissionUser = JsonPath.root.submissionUser.string

  def apply(submittedBy: User = Properties.userName,
            matchMode: SelectionMode = SelectionFirst(),
            workMatcher: JMatcher = JMatcher.matchAll,
            awaitMatch : Boolean = false) = {
    val json = Json.obj("submissionUser" -> Json.fromString(submittedBy))
    new SubmissionDetails(json, matchMode, workMatcher, awaitMatch)
  }
}
