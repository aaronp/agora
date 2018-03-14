package agora.json

import agora.BaseJsonSpec
import io.circe._

class JPredicateTest extends BaseJsonSpec {

  import JPredicate._
  import JPredicate.implicits._
  import io.circe.syntax._

  Seq[JPredicate](
    Eq("foo"),
    Gt("foo"),
    Gte("foo"),
    Lt("foo"),
    Lte("foo"),
    Not(Eq(123)),
    And(Eq(1), Eq(2)),
    Or(Eq(3), Eq(4)),
    JRegex("te.xt?"),
    JIncludes(Set(Json.fromString("value")))
  ).foreach { pred =>
    pred.toString should {
      s"be serializable from ${pred.asJson.noSpaces}" in {
        val Right(backAgain) = pred.asJson.as[JPredicate]
        backAgain shouldBe pred
      }
    }
  }

  "Before" should {
    "evaluate 'health.asOf' before '1 day ago' " in {
      val before = JPath("health") :+ ("asOf" before "1 day ago")
      before.asMatcher().matches(hocon""" health.asOf : "yesterday" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "1 day ago" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "2 days ago" """) shouldBe true
      before.asMatcher().matches(hocon""" health.asOf : "1 hour ago" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "23 hours ago" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "25 hours ago" """) shouldBe true
      before.asMatcher().matches(hocon""" health.asOf : "2017-07-03T10:15:30" """) shouldBe true
      before.asMatcher().matches(hocon""" health.asOf : "meh" """) shouldBe false
    }
  }
  "After" should {
    "evaluate 'health.asOf' after '1 day ago' " in {
      val before = JPath("health") :+ ("asOf" after "1 day ago")
      before.asMatcher().matches(hocon""" health.asOf : "2 days ago" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "1 hour ago" """) shouldBe true
      before.asMatcher().matches(hocon""" health.asOf : "23 hours ago" """) shouldBe true
      before.asMatcher().matches(hocon""" health.asOf : "25 hours ago" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "2017-07-03T10:15:30" """) shouldBe false
      before.asMatcher().matches(hocon""" health.asOf : "meh" """) shouldBe false
    }
  }

  "Eq" should {
    "evaluate 'a.b.c' eq 12" in {
      val eq = JPath("a", "b") :+ ("c" === 12)
      eq.asMatcher().matches(hocon"a.b.c : 12") shouldBe true
      eq.asMatcher().matches(hocon"a.b.c : 13") shouldBe false
    }
    "not match different types (e.g. 12 integer vs 12 as a string)" in {
      val eq = JPath("a", "b") :+ ("c" === 12)
      eq.asMatcher().matches(hocon""" a.b.c : 12 """) shouldBe true
      eq.asMatcher().matches(hocon""" a.b.c : "12" """) shouldBe false
    }
  }
  "Lte" should {
    "evaluate 'value' lte 12.34" in {
      val predicate = ("value" lte Json.fromBigDecimal(BigDecimal("12.34"))).asMatcher()
      predicate.matches(hocon"value : 12.35") shouldBe false
      predicate.matches(hocon"value : 12.34") shouldBe true
      predicate.matches(hocon"value : 12") shouldBe true
    }
  }
  "Lt" should {
    "evaluate 'value' lt 12.34" in {
      val predicate = ("value" lt Json.fromBigDecimal(BigDecimal("12.34"))).asMatcher()
      predicate.matches(hocon"value : 12.35") shouldBe false
      predicate.matches(hocon"value : 12.34") shouldBe false
      predicate.matches(hocon"value : 12") shouldBe true
    }
  }
  "Gte" should {
    "evaluate 'value' gte 12.34" in {
      val predicate = ("value" gte Json.fromBigDecimal(BigDecimal("12.34"))).asMatcher()
      predicate.matches(hocon"value : 12.35") shouldBe true
      predicate.matches(hocon"value : 12.34") shouldBe true
      predicate.matches(hocon"value : 12.33") shouldBe false
      predicate.matches(hocon"value : 12") shouldBe false
    }
  }
  "Gt" should {
    "evaluate 'value' gt 12.34" in {
      val predicate = ("value" gt Json.fromBigDecimal(BigDecimal("12.34"))).asMatcher()
      predicate.matches(hocon"value : 12.35") shouldBe true
      predicate.matches(hocon"value : 12.34") shouldBe false
      predicate.matches(hocon"value : 12") shouldBe false
    }
    "evaluate 'value' gt 12" in {
      val predicate = ("value" gt 12).asMatcher()
      predicate.matches(hocon"value : 11") shouldBe false
      predicate.matches(hocon"value : 12") shouldBe false
      predicate.matches(hocon"value : 13") shouldBe true
    }
  }
  "Before" should {
    "evaluate 'time' before '1 minute ago' " in {}
  }
  "json includes" should {

    def jsonList(theRest: String*) = Map("list" -> theRest.toList).asJson

    "match nested lists" in {

      val path = "nested".asJPath :+ "array".includes(Set("first", "last"))
      path.asMatcher().matches(Map("nested"        -> Map("array" -> List("first", "middle", "last"))).asJson) shouldBe true
      path.asMatcher().matches(Map("nested"        -> Map("array" -> List("middle", "last"))).asJson) shouldBe false
      path.asMatcher().matches(Map("differentRoot" -> Map("array" -> List("first", "middle", "last"))).asJson) shouldBe false
    }

    "match json which includes the given elements" in {
      val matcher = "list".includes(Set("first", "last")).asMatcher()
      matcher.matches(jsonList("first", "middle", "last")) shouldBe true
      matcher.matches(jsonList("", "last", "first", "middle")) shouldBe true
      matcher.matches(jsonList()) shouldBe false
      matcher.matches(jsonList("first", "middle")) shouldBe false
    }
    "match numeric elements" in {
      "list"
        .includes(Set(4, 5, 6))
        .asMatcher()
        .matches(Map("list" -> List(3, 4, 5, 6, 7)).asJson) shouldBe true
    }
    "return false when the element doesn't exist" in {
      "list".includes(Set(1)).asMatcher().matches(Map("different" -> List(1)).asJson) shouldBe false
    }
    "return true for any list when given an empty list" in {
      "list"
        .includes(Set.empty)
        .asMatcher()
        .matches(jsonList("first", "middle", "last")) shouldBe true
      "list".includes(Set.empty).asMatcher().matches(jsonList()) shouldBe true
      "list"
        .includes(Set.empty)
        .asMatcher()
        .matches(Map("list" -> Map("actuallyAnObj" -> 123)).asJson) shouldBe false
    }
  }

  "JPredicate.decoder" should {
    "unmarshal simple paths json" in {
      val json =
        json"""{
              |  "select" : [ "command" ],
              |  "test" : "match-all"
              |}"""

      val expected: JPredicate = JPath("command").asMatcher()
      json.as[JPredicate].right.get shouldBe expected
    }
    "unmarshal complex paths json" in {
      val json =
        json"""{
              |  "select" : [ "list", 2, "next" ],
              |  "test" : "match-all"
              |}"""
      json.as[JPredicate].right.get shouldBe JPath.forParts("list", "2", "next").asMatcher()
    }
    "unmarshal paths with a filter" in {
      val json =
        json"""{
              |  "select" : [
              |      "rute",
              |      {
              |        "field" : "someField",
              |        "predicate" : {
              |          "eq" : 4
              |        }
              |      }
              |    ],
              |  "test" : "match-all"
              |}"""
      val expected = JPath(JPart("rute"), ("someField" === 4))
      json.as[JPredicate].right.get shouldBe expected.asMatcher()
    }
    "unmarshal paths with conjunctions" in {

      val json =
        json"""{
              |  "or" : [
              |    {
              |      "and" : [
              |        {
              |          "select" : [
              |              {
              |                "field" : "array",
              |                "predicate" : {
              |                  "elements" : [
              |                    9,
              |                    8
              |                  ]
              |                }
              |              }
              |            ],
              |          "test" : "match-all"
              |        },
              |        {
              |          "select" : [
              |              {
              |                "field" : "foo",
              |                "predicate" : {
              |                  "gte" : 3
              |                }
              |              }
              |            ],
              |          "test" : "match-all"
              |        }
              |      ]
              |    },
              |    {
              |      "select" : [
              |          "x",
              |          "y",
              |          {
              |            "field" : "values",
              |            "predicate" : {
              |              "regex" : "subtext"
              |            }
              |          }
              |        ],
              |      "test" : "match-all"
              |    }
              |  ]
              |}"""

      import AgoraJsonImplicits._

      val expected: JPredicate =
        ("array" includes (8, 9)).and("foo" gte 3).or(JPath("x", "y") :+ ("values" ~= ("subtext")))
      json.as[JPredicate].right.get shouldBe expected
    }
  }

  "JPredicate.or" should {

    "be serializable to/from json" in {
      val matcher: JPredicate = JPredicate.matchAll or JPredicate.matchAll

      val json             = matcher.asJson
      val Right(backAgain) = json.as[JPredicate]
      matcher shouldBe backAgain
    }
  }
  "JPredicate.and" should {
    "be serializable to/from json" in {

      val matcher1 = JPredicate(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val matcher2 = JPredicate(JPath("cpus" gt "2"))

      val matcher: JPredicate = matcher1 and matcher2

      val json             = matcher.asJson
      val Right(backAgain) = json.as[JPredicate]
      matcher shouldBe backAgain
    }
  }
  "JPredicate.exists" should {
    "be serializable to/from json" in {
      val exists: JPredicate = JPredicate(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val json               = exists.asJson
      val Right(backAgain)   = json.as[JPredicate]
      exists shouldBe backAgain
    }
  }
}
