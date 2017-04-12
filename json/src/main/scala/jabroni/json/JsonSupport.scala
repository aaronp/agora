package jabroni.json

import io.circe.Decoder.Result
import language.implicitConversions

trait JsonSupport {

  import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.optics._
  import cats.syntax.either._
  // import io.circe._
  // import io.circe.generic.auto._
  // import io.circe.parser._
  // import io.circe.syntax._

  val json : Json = parse(
    """{
      |  "x" : {
      |    "y" : {
      |      "z" : [
      |      1,2,3
      |
      |
      |      ]
      |
      |    }
      |
      |  }
      |}
    """.stripMargin).getOrElse(Json.Null)

  val p = JsonPath.root.x.y.z.each.int

  val foo = p.getAll(json)

  println(foo)

}
