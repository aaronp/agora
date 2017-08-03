package agora.api.json

import org.scalatest.{Matchers, WordSpec}

class JMatcherTest extends WordSpec with Matchers {

  import JPredicate.implicits._
  import io.circe.syntax._

  implicit def asRichString(str: String) = new {
    def asJson = {
      _root_.io.circe.parser.parse(str.stripMargin).right.get
    }
  }

  "JMatcher.decoder" should {
    "unmarshal simple paths json" in {
      val json =
        """{
          |  "exists" : {
          |    "parts" : [
          |      { "name" : "command" }
          |    ]
          |  }
          |}""".asJson
      json.as[JMatcher].right.get shouldBe JPath("command").asMatcher
    }
    "unmarshal complex paths json" in {
      val json =
        """{
          |  "exists" : {
          |    "parts" : [
          |      { "name" : "list" },
          |      { "pos" : 2 },
          |      { "name" : "next" }
          |    ]
          |  }
          |}""".asJson
      json.as[JMatcher].right.get shouldBe JPath(JPart("list"), JPos(2), JPart("next")).asMatcher
    }
    "unmarshal paths with a filter" in {
      val json =
        """{
          |  "exists" : {
          |    "parts" : [
          |      { "name" : "rute" },
          |      {
          |        "field" : {
          |           "eq" : 4
          |        }
          |      }
          |    ]
          |  }
          |}""".asJson
      json.as[JMatcher].right.get shouldBe JPath(JPart("rute"), ("field" === 4)).asMatcher
    }
  }

  "JMatcher.or" should {

    "be serializable to/from json" in {
      val matcher: JMatcher = JMatcher.matchAll or JMatcher.matchAll

      val json             = matcher.asJson
      val Right(backAgain) = json.as[JMatcher]
      matcher shouldBe backAgain
    }
  }
  "JMatcher.and" should {
    "be serializable to/from json" in {

      val matcher1 = JMatcher(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val matcher2 = JMatcher(JPath("cpus" gt "2"))

      val matcher: JMatcher = matcher1 and matcher2

      val json             = matcher.asJson
      val Right(backAgain) = json.as[JMatcher]
      matcher shouldBe backAgain
    }
  }
  "JMatcher.exists" should {
    "be serializable to/from json" in {
      val exists: JMatcher = JMatcher(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val json             = exists.asJson
      val Right(backAgain) = json.as[JMatcher]
      exists shouldBe backAgain
    }
  }
}
