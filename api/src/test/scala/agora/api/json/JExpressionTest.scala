package agora.api.json

import agora.BaseSpec
import agora.api.json.JExpression.implicits._
import io.circe.Json

class JExpressionTest extends BaseSpec {
  "JPathExpression" should {
    "select the json at the given jpath" in {
      val path = JPath("foo", "bar").asExpression

      path.eval(json"""{ "foo" : { "bar" : { "original" : true} } }""") shouldBe Some(json"""{ "original" : true} """)
      path.eval(json"""{ "bar" : { "original" : true} }""") shouldBe None
      path.eval(json"""{ "foo" : { "original" : true} }""") shouldBe None
    }
  }
  "JMergeExpression" should {
    "be able to merge two json expressions" in {

      val constant = json"""{ "meh" : 123 }""".asExpression
      val path     = JPath("foo", "bar").asExpression

      val expr: JExpression = path.merge(constant)

      val Some(merged) = expr.eval(json"""{ "foo" : { "bar" : { "original" : true} } }""")
      merged shouldBe
        json"""{
                            "meh" : 123,
                            "original" : true
                          } """
    }
  }
  "JStringExpression" should {
    "be able to concat" in {
      val actual = Json.fromString("foo").asExpression.concat(Json.fromString("bar").asExpression).eval(json"""{ "doesn'" : "matter" }""")

      actual shouldBe Some(Json.fromString("foobar"))
    }
  }
  "JNumericExpression" should {
    val longPath   = JPath("long").asExpression
    val doublePath = JPath("double").asExpression
    val intPath    = JPath("int").asExpression

    val jsonDoc =
      json"""{ 
            "int" : 123,
            "long" : 3223372036854775807,
            "double" : 123.456 
            }"""

    "be able to add" in {
      (intPath + intPath).eval(jsonDoc) shouldBe Some(Json.fromInt(246))
    }
    "be able to subtract" in {
      (intPath - intPath).eval(jsonDoc) shouldBe Some(Json.fromInt(0))
      (longPath - intPath).eval(jsonDoc) shouldBe Some(Json.fromLong(3223372036854775684L))
    }
    "be able to multiply" in {
      (intPath * intPath).eval(jsonDoc) shouldBe Some(Json.fromInt(123 * 123))
      (intPath * doublePath).eval(jsonDoc) shouldBe Some(Json.fromDouble(123 * 123.456D))
    }
    "be able to divide" in {
      (intPath / doublePath).eval(jsonDoc) shouldBe Some(Json.fromBigDecimal(BigDecimal(123) / 123.456D))
      (intPath / intPath).eval(jsonDoc) shouldBe Some(Json.fromDouble(1))
    }
    "be able to moduloificate" in {
      (longPath % intPath).eval(jsonDoc) shouldBe Some(Json.fromDouble(3223372036854775807L % 123))
    }
  }
}
