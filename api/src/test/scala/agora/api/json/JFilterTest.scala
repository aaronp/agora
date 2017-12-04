package agora.api.json

import agora.BaseSpec
import agora.api.Implicits._
import io.circe.syntax._

class JFilterTest extends BaseSpec {
  "JFilter" should {
    "pickle gte to/from json" in {
      val jfilterJson =
        json"""{
                "field" : "x",
                "predicate" : {
                  "gte" : 5
                }
                }"""

      val expected: JPart = "x" gte 5
      jfilterJson.as[JPart] shouldBe Right(expected)
    }
    "pickle to/from json" in {
      val jfilterJson =
        json"""{
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
                }"""

      val pred: JFilter   = "x" gte 5
      val expected: JPart = JFilter("someField", pred)

      jfilterJson.as[JPart] shouldBe Right(expected)
    }
  }
}
