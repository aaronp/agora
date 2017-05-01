package jabroni.api.exchange

import jabroni.api.json.{JPart, JPath}
import org.scalatest.{FunSuite, Matchers, WordSpec}

class SelectionModeTest extends WordSpec with Matchers {

  Seq(
    SelectionMode.first(),
    SelectionMode.all(),
    SelectionMode(3, false),
    SelectionMode.max(JPath(JPart("field"), JPart(1), JPart("meh")))
  ).foreach { selMode =>

    selMode.toString should {
      "be serializable to/from json" in {
        import io.circe._
        import io.circe.syntax._
        import io.circe.generic._
        import io.circe.generic.auto._

        val json = selMode.asJson
        println(
          s"""
             |$selMode :
             |$json
             |or
             |${selMode.json}
           """.stripMargin)
        val Right(backAgain) = json.as[SelectionMode]
        backAgain shouldBe selMode
      }
    }
  }
}
