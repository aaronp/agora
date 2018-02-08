package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._
import io.circe.syntax._

import scala.language.reflectiveCalls

class SubmitJobTest extends BaseSpec {

  "SubmitJob.json" should {
    "marshal w/ custom worker buckets" in {
      val jsonStr =
        json"""{
              |    "job" : "run",
              |    "submissionDetails" : {
              |        "aboutMe" : {
              |            "submissionUser" : "Meh"
              |        },
              |        "selection" : "select-one",
              |        "awaitMatch" : true,
              |        "workMatcher" : {
              |            "criteria" : "match-all",
              |            "buckets" : [ ],
              |            "updates" : [ ]
              |        },
              |        "orElse" : [
              |        ]
              |    }
              |}"""

      val actual = "run".asJob.withDetails(SubmissionDetails().add("submissionUser" -> "Meh")).asJson
      actual shouldBe jsonStr
    }
    "unmarshal w/o worker buckets" in {
      val expectedJob = "run".asJob.withDetails(SubmissionDetails().add("submissionUser" -> "Meh"))

      val jsonStr =
        json"""{
            "job" : "run",
            "submissionDetails" : {
                "aboutMe" : {
                    "submissionUser" : "Meh"
                },
                "selection" : "select-one",
                "awaitMatch" : true,
                "workMatcher" : {
                    "criteria" : "match-all",
                    "buckets" : [
                    ],
                    "updates" : [
                    ]
                },
                "orElse" : [
                ]
            }
        }"""

      jsonStr.as[SubmitJob] shouldBe Right(expectedJob)
    }

  }
  "SubmitJob.withId" should {
    "overwrite previous values" in {
      "something".asJob.withId("first").withId("second").jobId shouldBe Option("second")
    }
  }
  "SubmitJob" should {
    "be able to get the json back" in {

      "foo".asJob shouldBe "foo".asJob
      "foo".asJob should not equal ("foo".asJob.add("key" -> "value"))
      "foo".asJob.add("key"                               -> "value") shouldBe ("foo".asJob.add("key" -> "value"))
    }
  }
}
