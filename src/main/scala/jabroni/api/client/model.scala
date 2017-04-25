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
                             selection: SelectionMode[Json],
                             workMatcher: JMatcher) {

  def submittedBy: User = SubmissionDetails.submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }
}


object SubmissionDetails {

  def submissionUser = JsonPath.root.submissionUser.string

  def apply(submittedBy: User = Properties.userName,
            matchMode: SelectionMode[Json]  = SelectionFirst(),
            workMatcher: JMatcher  = JMatcher.matchAll) = {

    val json = Json.obj("submissionUser" -> Json.fromString(submittedBy))
    new SubmissionDetails(json, matchMode, workMatcher)
  }
}
