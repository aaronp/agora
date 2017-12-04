package agora.rest

import agora.BaseSpec
import agora.api.exchange.{SelectionFirst, SelectionMode, SubmissionDetails, WorkMatcher}
import agora.api.json.{MatchAll, MatchNone}
import io.circe.optics.JsonPath

import scala.util.Properties

class ClientConfigTest extends BaseSpec {
  "ClientConfig.load" should {
    "parse its config" in {
      val conf = ClientConfig.load()
      conf.submissionDetails.workMatcher.workerBucket.isEmpty shouldBe true
    }
  }
  "ClientConfig.submissionDetailsFromConfig" should {
    import agora.api.Implicits._
    import io.circe.syntax._

    "parse a config" in {
      val conf =
        conf"""
        details : { }
        awaitMatch : true
        workMatcher : {
          criteria : match-none
          buckets : []
          bucketsIncludeMissingValues : false
        }
        selectionMode : select-first
        orElse : [ ]
        """

      val details: SubmissionDetails = ClientConfig.submissionDetailsFromConfig(conf)

      details.awaitMatch shouldBe true
      details.selection shouldBe SelectionFirst()
      details.workMatcher shouldBe WorkMatcher(MatchNone)
      details.orElse shouldBe empty
      SubmissionDetails.submissionUser.getOption(details.aboutMe) shouldBe Option(Properties.userName)
    }
    "parse a complex config" in {
      val matcher = ("topic" === "testing") or ("list".asJPath :+ ("values" includes 3)) and ("foo" ~= "b.r")

      val mode = SelectionMode.max("machine.cpus".asJPath)
      val json = matcher.asJson

      val conf =
        conf"""
        details : {
           hi : there
           submissionUser : dave
        }
        awaitMatch : false
        workMatcher : {
          criteria : ${json}
          buckets : []
          bucketsIncludeMissingValues : false
        }
        selectionMode : {
           max : [ "machine", "cpus" ]
        }
        orElse : [
          {
             match : match-all
          },
          {
             match : match-none
          }
        ]
        """

      val details: SubmissionDetails = ClientConfig.submissionDetailsFromConfig(conf)
      JsonPath.root.hi.string.getOption(details.aboutMe) shouldBe Some("there")

      details.awaitMatch shouldBe false
      details.selection shouldBe mode
      details.workMatcher shouldBe WorkMatcher(matcher)
      details.orElse should contain only (WorkMatcher(MatchAll), WorkMatcher(MatchNone))
      SubmissionDetails.submissionUser.getOption(details.aboutMe) shouldBe Option("dave")
    }
  }

}
