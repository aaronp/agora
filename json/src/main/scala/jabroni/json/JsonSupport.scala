package jabroni.json

import io.circe.Decoder.Result
import language.implicitConversions

trait JsonSupport {

  import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.optics._
  // import io.circe._
  // import io.circe.generic.auto._
  // import io.circe.parser._
  // import io.circe.syntax._

  val p : JsonPath = null
  println(p)

}
