package jabroni.api.json

import io.circe.{HCursor, Json}
import org.scalatest.{Matchers, WordSpec}
import language.implicitConversions

class JPathTest extends WordSpec with Matchers {

  import io.circe.parser._

  implicit def strAsJson(s: String) = parse(s).right.get

  "JPath.select" should {
    "traverse JFields" in {
      val json: Json =
        """{ "foo" : 1 } """

      JPath.select(JField("foo") :: Nil, json.hcursor) match {
        case h: HCursor => h.value.asNumber.flatMap(_.toInt).get shouldBe 1
      }
      val a = JPath.select(JField("bar") :: Nil, json.hcursor)
      a.succeeded shouldBe false
      a.focus.toList should be(empty)
    }
  }
}
