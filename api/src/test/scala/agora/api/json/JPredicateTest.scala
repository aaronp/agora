package agora.api.json

import org.scalatest.{Matchers, WordSpec}

class JPredicateTest extends WordSpec with Matchers {

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
    JRegex("te.xt?")
  ).foreach { pred =>
    pred.toString should {
      s"be serializable from ${pred.asJson.noSpaces}" in {
        val Right(backAgain) = pred.asJson.as[JPredicate]
        backAgain shouldBe pred
      }
    }
  }

  "json includes" should {

    def jsonList(theRest: String*) = Map("list" -> theRest.toList).asJson

    "match nested lists" in {

      val path = "nested".asJPath :+ "array".includes(Set("first", "last"))
      path.asMatcher.matches(Map("nested"        -> Map("array" -> List("first", "middle", "last"))).asJson) shouldBe true
      path.asMatcher.matches(Map("nested"        -> Map("array" -> List("middle", "last"))).asJson) shouldBe false
      path.asMatcher.matches(Map("differentRoot" -> Map("array" -> List("first", "middle", "last"))).asJson) shouldBe false
    }

    "match json which includes the given elements" in {
      val matcher = "list".includes(Set("first", "last")).asMatcher
      matcher.matches(jsonList("first", "middle", "last")) shouldBe true
      matcher.matches(jsonList("", "last", "first", "middle")) shouldBe true
      matcher.matches(jsonList()) shouldBe false
      matcher.matches(jsonList("first", "middle")) shouldBe false
    }
    "match numeric elements" in {
      "list".includes(Set(4, 5, 6)).asMatcher.matches(Map("list" -> List(3, 4, 5, 6, 7)).asJson) shouldBe true
    }
    "return false when the element doesn't exist" in {
      "list".includes(Set(1)).asMatcher.matches(Map("different" -> List(1)).asJson) shouldBe false
    }
    "return true for any list when given an empty list" in {
      "list".includes(Set.empty).asMatcher.matches(jsonList("first", "middle", "last")) shouldBe true
      "list".includes(Set.empty).asMatcher.matches(jsonList()) shouldBe true
      "list".includes(Set.empty).asMatcher.matches(Map("list" -> Map("actuallyAnObj" -> 123)).asJson) shouldBe false
    }
  }
}
