package jabroni.api.client

import io.circe.optics.JsonPath
import io.circe.{Encoder, Json}
import jabroni.api.User
import jabroni.api.exchange.{SelectionFirst, SelectionMode}
import jabroni.api.json.{JMatcher, JsonAppendable}

import scala.util.Properties


/**
  * Contains instructions/information specific to the job scheduling/matching
  */
case class SubmissionDetails(aboutMe: Json,
                             selection: SelectionMode,
                             workMatcher: JMatcher) extends JsonAppendable {

  def submittedBy: User = SubmissionDetails.submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }

  def withData[T: Encoder](data: T, name: String = null): SubmissionDetails = {
    val namespace = Option(name).getOrElse(data.getClass.getSimpleName)
    val json: Json = implicitly[Encoder[T]].apply(data)
    val qualified = Json.obj(namespace -> json)
    copy(aboutMe = qualified.deepMerge(aboutMe))
  }
}


object SubmissionDetails {

  def submissionUser = JsonPath.root.submissionUser.string

  def apply(submittedBy: User = Properties.userName,
            matchMode: SelectionMode = SelectionFirst(),
            workMatcher: JMatcher = JMatcher.matchAll) = {

    val json = Json.obj("submissionUser" -> Json.fromString(submittedBy))
    new SubmissionDetails(json, matchMode, workMatcher)
  }
}
