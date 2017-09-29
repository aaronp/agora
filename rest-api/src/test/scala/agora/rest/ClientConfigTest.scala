package agora.rest

import agora.BaseSpec
import agora.api.exchange.{SelectionFirst, SelectionMode, SubmissionDetails}
import agora.api.json.{MatchAll, MatchNone}
import io.circe.optics.JsonPath

import scala.util.Properties

class ClientConfigTest extends BaseSpec {
  "ClientConfig.submissionDetailsFromConfig" should {
    import agora.api.Implicits._
    import io.circe.syntax._

    "parse a config" in {
      val conf =
        conf"""
        details : { }
        awaitMatch : true
        matcher : match-none
        selectionMode : select-first
        orElse : [ ]
        """

      val details: SubmissionDetails = ClientConfig.submissionDetailsFromConfig(conf)

      details.awaitMatch shouldBe true
      details.selection shouldBe SelectionFirst()
      details.workMatcher shouldBe MatchNone
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
        matcher : ${json}
        selectionMode : {
           max.parts : [ { "name" : "machine" }, { "name" : "cpus" } ]
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
      details.workMatcher shouldBe matcher
      details.orElse should contain only (MatchAll, MatchNone)
      SubmissionDetails.submissionUser.getOption(details.aboutMe) shouldBe Option("dave")
    }
  }

}
