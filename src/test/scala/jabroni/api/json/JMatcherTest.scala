package jabroni.api.json

import org.scalatest.{Matchers, WordSpec}

class JMatcherTest extends WordSpec with Matchers {

  "JMatcher" should {
    "be serializable to/from json" in {
      import JPredicate.implicits._

      val matcher1 = JMatcher(JPath(JPart("foo"), JPart("bar"), JPart(3), "value" === "3"))
      val matcher2 = JMatcher(JPath("cpus" gt "2"))

      val matcher: JMatcher = matcher1 && matcher2
      import io.circe.syntax._

      val json = matcher.asJson
      println(json)
      val backAgain = json.as[JMatcher]
      matcher shouldBe backAgain
    }
  }
}
