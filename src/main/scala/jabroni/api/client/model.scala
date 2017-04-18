package jabroni.api.client

import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.api.exchange.{SelectionFirst, SelectionMode}
import jabroni.api.User
import jabroni.api.json.JMatcher

import scala.util.Properties


/**
  * Contains instructions/information specific to the job scheduling/matching
  */
case class SubmissionDetails(aboutMe: Json,
                             selection: SelectionMode,
                             workMatcher: JMatcher) {

  import SubmissionDetails._

  def submittedBy: User = submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }
}


object SubmissionDetails {

  val submissionUser = JsonPath.root.submissionUser.string

  case class DefaultDetails(submissionUser: String)

  def apply(submittedBy: User = Properties.userName,
            matchMode: SelectionMode = SelectionFirst(),
            workMatcher: JMatcher = JMatcher.matchAll) = {
    import io.circe.generic._

    import io.circe.generic.auto._
    import io.circe.parser._
    import io.circe.syntax._
    val json = DefaultDetails(submittedBy).asJson
    new SubmissionDetails(json, matchMode, workMatcher)
  }
}
