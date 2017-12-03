package agora.rest.worker

import agora.BaseSpec
import agora.api.json.{JPredicate, JPath, MatchAll, MatchNone}
import agora.api.worker.HostLocation
import com.typesafe.config.ConfigFactory

class SubscriptionConfigTest extends BaseSpec {

  "SubscriptionConfig.apply" should {
    "parse complex match criteria" in {
      val conf             = parse("""
          |  details: {
          |    runUser: dave
          |    path: "x/y/z"
          |    name: carl
          |    id: ""
          |  }
          |  jobCriteria: "match-all"
          |  submissionCriteria: "match-none"
          |  subscriptionReferences : [x]
          |""".stripMargin)
      val expectedLocation = HostLocation.localhost(123)
      val details          = conf.workerDetails(expectedLocation)
      details.location shouldBe expectedLocation
      details.runUser shouldBe "dave"
      details.path shouldBe "x/y/z"
      details.name shouldBe Some("carl")
      details.subscriptionKey shouldBe None

      val subscription = conf.subscription(expectedLocation)
      subscription.jobCriteria shouldBe MatchAll
      subscription.submissionCriteria shouldBe MatchNone
      subscription.subscriptionReferences should contain only ("x")
    }
  }

  "SubscriptionConfig.asMatcher" should {
    "parse complex match criteria" in {

      val criteria =
        """{
          |  "or" : [
          |    {
          |      "and" : [
          |        {
          |          "select" : {
          |            "parts" : [
          |              {
          |                "field" : "x",
          |                "predicate" : {
          |                  "eq" : "y"
          |                }
          |              }
          |            ]
          |          },
          |          "test" : "match-all"
          |        },
          |        {
          |          "select" : {
          |            "parts" : [
          |              {
          |                "field" : "foo",
          |                "predicate" : {
          |                  "gte" : 3
          |                }
          |              }
          |            ]
          |          },
          |          "test" : "match-all"
          |        }
          |      ]
          |    },
          |    {
          |      "select" : {
          |        "parts" : [
          |          "x", "y", "z"
          |        ]
          |      },
          |      "test" : "match-all"
          |    }
          |  ]
          |}""".stripMargin

      import agora.api.Implicits._

      val expected: JPredicate = ("x" === "y").and("foo" gte 3).or(JPath("x", "y", "z"))

      val conf = parse(s"""complex: $criteria""")
      conf.asMatcher("complex") shouldBe Right(expected)
    }
  }

  def parse(str: String) = SubscriptionConfig(ConfigFactory.parseString(str))
}
