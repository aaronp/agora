package agora.json

import agora.BaseJsonSpec
import agora.json.JExpression.implicits._
import io.circe.Json
import io.circe.syntax._

class JExpressionTest extends BaseJsonSpec {
  "JExpression" should {
    List[JExpression](
      JConstantExpression(Option(json"""{"x" : "y"}""")),
      JMergeExpression(json"""{"x1" : "y1"}""".asExpression, json"""{"x2" : "y2"}""".asExpression),
      JNumericExpression(Json.fromInt(1).asExpression, Json.fromInt(2).asExpression, DivideOp),
      JNumericExpression(Json.fromInt(1).asExpression, Json.fromInt(2).asExpression, AddOp),
      JNumericExpression(Json.fromInt(1).asExpression, Json.fromInt(2).asExpression, MultiplyOp),
      JNumericExpression(Json.fromInt(1).asExpression, Json.fromInt(2).asExpression, SubstractOp),
      JNumericExpression(Json.fromInt(1).asExpression, Json.fromInt(2).asExpression, ModuloOp),
      JPathExpression(JPath("hi")),
      JStringExpression(Json.fromString("x").asExpression, Json.fromString("y").asExpression, ConcatString)
    ).foreach { expected =>
      s"marshal ${expected} to/from json" in {
        val json = expected.asJson
        json.as[JExpression] shouldBe Right(expected)
      }
    }
  }
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
        json"""{ "meh" : 123,
                 "original" : true } """
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
      (intPath * doublePath).eval(jsonDoc) shouldBe Json.fromDouble(123 * 123.456D)
    }
    "be able to divide" in {
      (intPath / doublePath).eval(jsonDoc) shouldBe Some(Json.fromBigDecimal(BigDecimal(123) / 123.456D))
      (intPath / intPath).eval(jsonDoc) shouldBe Json.fromDouble(1)
    }
    "be able to moduloificate" in {
      (longPath % intPath).eval(jsonDoc) shouldBe Json.fromDouble(3223372036854775807L % 123)
    }
  }
}
