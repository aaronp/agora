package jabroni.api.json

import io.circe.parser.parse
import io.circe.{HCursor, Json}
import org.scalatest.{Matchers, WordSpec}

import scala.language.implicitConversions

class JPathTest extends WordSpec with Matchers {

  import JPathTest._

  //  "JPath.json" should {
  //
  //    // TODO ... it does
  //    "not look shit" in {
  //
  //      import jabroni.api.Implicits._
  //
  //      val filter: JPart = "x" lte 1
  //      val path = filter +: JPath("foo", "bar", "3")
//
  //  {
  //    "parts" : [
  //    {
  //      "field" : "x",
  //      "predicate" : {
  //        "lte" : 1
  //      }
  //    },
  //    {
  //      "name" : "foo"
  //    },
  //    {
  //      "name" : "bar"
  //    },
  //    {
  //      "i" : 3
  //    }
  //    ]
  //  }
  //
  //      println(path.json.spaces2)
  //      println(path.json.noSpaces)
  //    }
  //  }
  "JPath.select" should {
    "match JFields" in {
      val json: Json =
        json"""{ "foo" : 1 } """

      JPath.select(JField("foo") :: Nil, json.hcursor) match {
        case h: HCursor => h.value.asNumber.flatMap(_.toInt).get shouldBe 1
      }
      val a = JPath.select(JField("bar") :: Nil, json.hcursor)
      a.succeeded shouldBe false
      a.focus.toList should be(empty)
    }
    "match JPos" in {
      val json: Json = json"""[1,2,3]"""

      def intAt(n: Int) = JPath.select(JPos(n) :: Nil, json.hcursor) match {
        case h: HCursor => h.value.asNumber.flatMap(_.toInt).get
      }

      intAt(1) shouldBe 2
      intAt(0) shouldBe 1
      intAt(2) shouldBe 3

      val a = JPath.select(JPos(3) :: Nil, json.hcursor)
      a.succeeded shouldBe false
      a.focus.toList should be(empty)
    }
  }
  "match JFilter" in {
    val json: Json = json"""{ "some-field" : 456 }"""

    import JPredicate.implicits._
    val found = JPath.select(("some-field" === 456) :: Nil, json.hcursor)
    found.succeeded shouldBe true
    val found2 = JPath.select(("some-field" === 789) :: Nil, json.hcursor)
    found2.succeeded shouldBe false
  }

}

object JPathTest {


  implicit class JsonHelper(sc: StringContext) {
    def json(args: Any*) = {
      val text = sc.s(args: _*)
      parse(text).right.get
    }
  }

}