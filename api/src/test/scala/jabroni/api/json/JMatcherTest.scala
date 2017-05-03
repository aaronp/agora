package jabroni.api.json

import org.scalatest.{Matchers, WordSpec}

class JMatcherTest extends WordSpec with Matchers {

  import JPredicate.implicits._

  import io.circe.syntax._

  "JMatcher.or" should {

    "be serializable to/from json" in {
      val matcher: JMatcher = JMatcher.matchAll or JMatcher.matchAll

      val json = matcher.asJson
      println(json)
      val Right(backAgain) = json.as[JMatcher]
      matcher shouldBe backAgain
    }
  }
  "JMatcher.and" should {
    "be serializable to/from json" in {

      val matcher1 = JMatcher(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val matcher2 = JMatcher(JPath("cpus" gt "2"))

      val matcher: JMatcher = matcher1 and matcher2

      val json = matcher.asJson
      val Right(backAgain) = json.as[JMatcher]
      matcher shouldBe backAgain
    }
  }
  "JMatcher.exists" should {
    "be serializable to/from json" in {
      val exists: JMatcher = JMatcher(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val json = exists.asJson
      val Right(backAgain) = json.as[JMatcher]
      exists shouldBe backAgain
    }
  }
}
