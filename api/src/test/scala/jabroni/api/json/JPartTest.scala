package jabroni.api.json

import io.circe.optics.{JsonPath, JsonTraversalPath}
import org.scalatest.{FunSuite, Matchers, WordSpec}
import language.implicitConversions

class JPartTest extends WordSpec with Matchers {

  import io.circe._
  import io.circe.parser._
  "JField" should {
    "match" in {
      implicit def str2Json(s : String) = parse(s).right.get

//      val p: Either[JsonTraversalPath, JsonPath] = JField("foo").advance("""{ "foo" : 1 }""")
    }
  }
}
