package agora.json

import agora.BaseJsonSpec
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

class JsonDiffTest extends BaseJsonSpec {

  "JsonDiff.apply" should {

    List(
      (json"""1""", json"""2"""),
      (json"""1""", json""" "a" """),
      (json""" 1 """, json""" "1" """),
      (json"""1""", json"""true"""),
      (json"""[1,2,3]""", json"""[1,2]"""),
      (json"""[1,2,3]""", json"""[3,2,1]""")
    ).foreach {
      case (lhs, rhs) =>
        s"produce a simple value diff for ${lhs} and $rhs" in {
          JsonDiff(lhs, rhs) shouldBe JsonDiff(DiffEntry(lhs, rhs) :: Nil)
        }
    }

    List(
      (json"""1""", json"""1"""),
      (json""" "a" """, json""" "a" """),
      (json"""false""", json"""false"""),
      (json"""[1,2,3]""", json"""[1,2,3]"""),
      (json"""[]""", json"""[]""")
    ).foreach {
      case (lhs, rhs) =>
        s"NOT produce a diff for ${lhs} and $rhs" in {
          JsonDiff(lhs, rhs) shouldBe JsonDiff(Nil)
        }
    }

    "diff two nested values" in {

      val lhs =
        json"""{
                  "only left" : { "left" : "is best" },
                  "same nested" : { "both" : true },
                  "different nested" : {
                     "a" : true,
                     "b" : 123,
                     "deep" : {
                       "array Same" : [ "a","b", "c" ],
                       "array different" : [ "d","e","f"]
                     }
                  } 
                }"""
      val rhs =
        json""" {
                  "only right" : { "right" : "is best" },
                  "different nested" : {
                     "a" : false,
                     "b" : 123,
                     "deep" : {
                       "array different" : ["h","i","j"],
                       "array Same" : ["a","b","c"]
                     }
                  },
                  "same nested" : { "both" : true }
                }"""
      val JsonDiff(deltas) = JsonDiff(lhs, rhs)

      deltas.size shouldBe 4
      deltas should contain(DiffEntry(List("only right"), Json.Null, json"""{ "right" : "is best" } """))
      deltas should contain(DiffEntry(List("different nested", "a"), Json.fromBoolean(true), Json.fromBoolean(false)))
      deltas should contain(DiffEntry(List("different nested", "deep", "array different"), json""" ["d", "e", "f" ]""", json""" [ "h", "i", "j" ] """))
      deltas should contain(DiffEntry(List("only left"), json""" { "left" : "is best" } """, Json.Null))

    }
    "return an empty diff for the same values" in {

      val deep =
        json""" {
                  "only right" : { "right" : "is best" },
                  "different nested" : {
                     "a" : false,
                     "b" : 123,
                     "deep" : {
                       "array different" : ["h","i","j"],
                       "array Same" : ["a","b","c"]
                     }
                  },
                  "same nested" : { "both" : true }
                }"""
      JsonDiff(deep, deep) shouldBe JsonDiff(Nil)
      JsonDiff(json"1", json"1") shouldBe JsonDiff(Nil)
      JsonDiff(json"null", json"null") shouldBe JsonDiff(Nil)
      JsonDiff(json"false", json"false") shouldBe JsonDiff(Nil)
    }
    "return an empty diff for the same values in a different order" in {

      val deep1 =
        json""" { "foo" : { "right" : "is best" },
                  "nested2" : { "both" : true },
                  "nested" : {
                     "b" : 123,
                     "a" : false,
                     "deep" : {
                       "array Same" : ["a","b","c"],
                       "array different" : ["h","i","j"]
                     }
                  }
                }"""
      val deep2 =
        json""" {
                  "nested" : {
                     "a" : false,
                     "b" : 123,
                     "deep" : {
                       "array different" : ["h","i","j"],
                       "array Same" : ["a","b","c"]
                     }
                  },
                  "foo" : { "right" : "is best" },
                  "nested2" : { "both" : true }
                }"""
      JsonDiff(deep1, deep2) shouldBe JsonDiff(Nil)
    }
  }
  "JsonDiff.asDelta" should {
    "be able to turn itself into a delta" in {
      val lhs =
        json"""{
            "a" : {
              "b" : {
                "c" : 123,
                "d" : false,
                "e" : [4,5,6]
              }
            }
       }"""

      val rhs =
        json"""{
            "a" : {
              "b" : {
                "c" : 123,
                "e" : [4,5,6,7]
              },
              "b2" : "meh"
            },
            "new" : "hi"
       }"""

      val diff = JsonDiff(lhs, rhs)
      diff.size shouldBe 4

      val delta = diff.asDelta
      delta.update(lhs) shouldBe rhs
    }
  }
  "JsonDiff.asJson" should {
    "be serialisable to json" in {

      val diff = JsonDiff(
        DiffEntry(List("a", "b"), json"""1""", json"""2""") ::
          DiffEntry(List(), json"""1""", Json.Null) ::
          DiffEntry(List("x"), Json.Null, json"""1""") ::
          Nil
      )

      diff.asJson shouldBe
        json"""{
            "deltas" : [
                {
                    "path" : [ "a", "b" ],
                    "lhs" : 1,
                    "rhs" : 2
                },
                {
                    "path" : [],
                    "lhs" : 1,
                    "rhs" : null
                },
                {
                    "path" : [ "x" ],
                    "lhs" : null,
                    "rhs" : 1
                }
            ]
        }"""
    }
  }
}
