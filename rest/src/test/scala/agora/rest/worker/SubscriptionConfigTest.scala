package agora.rest.worker

import agora.api.json.{JMatcher, JPath, MatchAll, MatchNone}
import agora.api.worker.HostLocation
import agora.rest.BaseSpec
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
          |  jobMatcher: "match-all"
          |  submissionMatcher: "match-none"
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
      subscription.jobMatcher shouldBe MatchAll
      subscription.submissionMatcher shouldBe MatchNone
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
          |          "exists" : {
          |            "parts" : [
          |              {
          |                "field" : "x",
          |                "predicate" : {
          |                  "eq" : "y"
          |                }
          |              }
          |            ]
          |          }
          |        },
          |        {
          |          "exists" : {
          |            "parts" : [
          |              {
          |                "field" : "foo",
          |                "predicate" : {
          |                  "gte" : 3
          |                }
          |              }
          |            ]
          |          }
          |        }
          |      ]
          |    },
          |    {
          |      "exists" : {
          |        "parts" : [
          |          {
          |            "name" : "x"
          |          },
          |          {
          |            "name" : "y"
          |          },
          |          {
          |            "name" : "z"
          |          }
          |        ]
          |      }
          |    }
          |  ]
          |}""".stripMargin

      import agora.api.Implicits._

      val expected: JMatcher = ("x" === "y").and("foo" gte 3).or(JPath("x", "y", "z"))

      val conf = parse(s"""complex: $criteria""")
      conf.asMatcher("complex") shouldBe Right(expected)
    }
  }

  def parse(str: String) = SubscriptionConfig(ConfigFactory.parseString(str))
}
