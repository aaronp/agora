package agora.api.exchange

import agora.BaseApiSpec
import agora.api.Implicits._
import agora.api.exchange.bucket.{BucketKey, JobBucket, WorkerMatchBucket}
import agora.json.{JExpression, JFilter, JPart, JPath, MatchAll, MatchNone}
import io.circe.Json
import io.circe.syntax._

class WorkMatcherTest extends BaseApiSpec {

  "WorkMatcher.decoder" should {
    "decode matchers with buckest and onMatchUpdates" in {
      val setSession: OnMatchUpdateAction = OnMatchUpdateAction.appendAction(JExpression(Json.fromString("newSession")), "session".asJPath)
      val incCounter                      = OnMatchUpdateAction.appendAction(JPath("counter").asExpression + Json.fromInt(1).asExpression, "session".asJPath)
      val expected =
        WorkMatcher("foo".asJPath, WorkerMatchBucket("foo".asJPath -> Json.fromString("someTopic")), onMatchUpdate = Vector(setSession, incCounter))

      val matcherJson = expected.asJson
      matcherJson.as[WorkMatcher] shouldBe Right(expected)
    }

    "decode matchers with empty buckets or onMatchUpdates" in {
      val matcherJson =
        json"""{
               "criteria" : "match-none",
               "buckets" : [ ],
               "onMatchUpdate" : [ ]
      }"""
      val result = matcherJson.as[WorkMatcher]
      result shouldBe Right(WorkMatcher(MatchNone))
    }
    "decode matchers with no buckets or onMatchUpdates" in {
      val matcherJson = json"""{ "criteria" : "match-none" }"""
      val result      = matcherJson.as[WorkMatcher]
      result shouldBe Right(WorkMatcher(MatchNone))
    }
  }
  "WorkMatcher.fromConfig" should {
    "parse a WorkMatcher from a simple configuration" in {
      val config =
        conf"""
            criteria : "match-all"
            buckets : []
            updates : []
           """

      val actual = WorkMatcher.fromConfig(config)
      actual shouldBe WorkMatcher(MatchAll, WorkerMatchBucket(Nil))
    }

    "parse a WorkMatcher from a complex configuration" in {
      val config =
        conf"""
               criteria : {
                  and : [
                      {
                          select : [
                              {
                                  field : "six",
                                  predicate : {
                                      gte : 5
                                  }
                              }
                          ],
                          test : "match-all"
                      },
                      {
                          select : [
                              {
                                  field : "seven",
                                  predicate : {
                                      lte : 8
                                  }
                              }
                          ],
                          test : "match-all"
                      }
                  ]
              }

              updates : [
                 {
                     "value" : { "const" : "newSession" },
                     "appendTo" : [ "session" ]
                 }
              ]

              buckets : [
                  {
                    "key" : {
                      "index" : [
                        "array",
                        1,
                        {
                          "field" : "someField",
                          "predicate" : {
                            "select" : [
                                {
                                  "field" : "x",
                                  "predicate" : {
                                    "gte" : 5
                                  }
                                }
                              ],
                            "test" : "match-all"
                          }
                        }
                      ],
                      "optional" : true
                    },
                    "value" : {
                      "complex" : "value"
                    }
                  },
                  {
                    "key" : {
                      "index" : [
                        "topic"
                      ],
                      "optional" : false
                    },
                    "value" : "foo"
                  }
                ]

              """

      val paths: List[JobBucket] = List(
        JobBucket(
          BucketKey(JPath(JPart("array"), JPart(1)) :+ JFilter("someField", "x" gte 5), true),
          json"""{ "complex" : "value"}"""
        ),
        JobBucket(BucketKey(JPath(JPart("topic")), false), Json.fromString("foo"))
      )
      val actual                          = WorkMatcher.fromConfig(config)
      val setSession: OnMatchUpdateAction = OnMatchUpdateAction.appendAction(JExpression(Json.fromString("newSession")), "session".asJPath)
      actual shouldBe WorkMatcher(("six" gte 5).and("seven" lte 8), WorkerMatchBucket(paths), Vector(setSession))
    }
  }
}
