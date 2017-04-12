package jabroni.api

import jabroni.api.json.JPath
import monocle.Optional
import org.scalatest.{Matchers, WordSpec}

class JPathTest extends WordSpec with Matchers {


  import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.optics._
  import JPathTest._

  "JPath.string" should {
    "return the values for a single json path" in {
      val simple = Simple("foo", Child("first"), List(Child("second"), Child("third")), Option(Child("second")))
      val json = simple.asJson
      println(json)
      val opt: Option[Option[Json]] = JsonPath.root.at("theRest").getOption(json)
      println(opt)
      JPath('first, 'name).string(json) should contain only ("first")
      JPath('theRest, 'name).string(simple.asJson) should contain only("second", "third")
//      JPath('theRest, 'each, 'name).string(simple.asJson) should contain only("second", "third")
    }
    "return the values for nested json paths" in {
      val simple = Recursive(Some("one"), Option(Recursive(Some("two"), Option(Recursive(Some("three"), None)))))
      val values = JPath('child, 'child, 'value).string(simple.asJson)
      values should contain only ("three")
    }
  }
}

object JPathTest {

  case class Recursive(value: Option[String] = None, child: Option[Recursive] = None)

  case class Child(name: String)

  case class Simple(name: String, first: Child, theRest: List[Child], favourite: Option[Child])

}