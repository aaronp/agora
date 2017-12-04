package agora.api.json

import agora.BaseSpec
import agora.api.Implicits._
import io.circe.parser._

class JArrayFindTest extends BaseSpec {
  "JArrayFind" should {
    "marshal to/from json" in {
      val input =
        """{
            "select" : [
              {
                "arrayFind" : {
                  "select" : [
                    {
                      "field" : "meh",
                      "predicate" : {
                        "eq" : "x"
                      }
                    }
                  ],
                  "test" : "match-all"
                }
              }
            ],
            "test" : "match-all"
          }"""

      val expected = JArrayFind("meh" === "x").asMatcher()
      decode[JPredicate](input) shouldBe Right(expected)
    }
  }
}
