package jabroni.api.client

import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.api.exchange.{MatchFirst, MatchMode}
import jabroni.api.User
import jabroni.api.json.JsonMatcher

import scala.util.Properties


/**
  * Contains instructions/information specific to the job scheduling/matching
  */
case class SubmissionDetails(aboutMe: Json,
                             matchMode: MatchMode,
                             workMatcher: JsonMatcher) {

  import SubmissionDetails._

  def submittedBy: User = submissionUser.getOption(aboutMe).getOrElse {
    sys.error(s"Invalid json, 'submissionUser' not set in $aboutMe")
  }
}


object SubmissionDetails {

  val submissionUser = JsonPath.root.submissionUser.string

  case class DefaultDetails(submissionUser: String)

  def apply(submittedBy: User = Properties.userName,
            matchMode: MatchMode = MatchFirst(true),
            workMatcher: JsonMatcher = JsonMatcher.matchAll) = {
    import io.circe.generic._

     import io.circe.generic.auto._
    import io.circe.parser._
    import io.circe.syntax._
    val json = DefaultDetails(submittedBy).asJson
    new SubmissionDetails(json, matchMode, workMatcher)
  }
}