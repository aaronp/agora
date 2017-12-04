package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._
import agora.api.exchange.bucket.{BucketKey, JobBucket, WorkerMatchBucket}
import agora.api.json.{JFilter, JPart, JPath, MatchAll}
import io.circe.Json

class WorkMatcherTest extends BaseSpec {

  "WorkMatcher.fromConfig" should {
    "parse a WorkMatcher from a simple configuration" in {
      val config =
        conf"""
            criteria : "match-all"
            buckets : []"""

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

      import io.circe.generic.auto._
      import io.circe.syntax._

      val paths: List[JobBucket] = List(
        JobBucket(
          BucketKey(JPath(JPart("array"), JPart(1)) :+ JFilter("someField", "x" gte 5), true),
          json"""{ "complex" : "value"}"""
        ),
        JobBucket(BucketKey(JPath(JPart("topic")), false), Json.fromString("foo"))
      )
      val actual = WorkMatcher.fromConfig(config)
      actual shouldBe WorkMatcher(("six" gte 5).and("seven" lte 8), WorkerMatchBucket(paths))
    }
  }
}
