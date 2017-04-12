package jabroni.api

import org.scalatest.{Matchers, WordSpec}
import io.circe.generic.auto._
import io.circe.optics.{JsonPath, JsonTraversalPath}
import io.circe.parser._
import io.circe.syntax._
import jabroni.api.json.JsonMatcher

class JsonMatcherTest extends WordSpec with Matchers {

  Seq[JsonMatcher](JsonMatcher.matchAll).foreach { m =>
    s"$m" should {
      "be serializable to and from json" in {
        val json = m.asJson
        println(json)
        decode[JsonMatcher](json.noSpaces) shouldBe Right(m)
      }
    }
  }

//  "JsonTraversalPath" should {
//    "be serializable to and from json" in {
//      val x: JsonTraversalPath = JsonPath.root.a.b.each.d.each.f
//      val json = x.asJson
//      println(json)
//      val backAgain = decode[JsonTraversalPath](json.noSpaces)
//      backAgain shouldBe x
//    }
//  }
//  "JsonPath" should {
//    "be serializable to and from json" in {
//      val x: JsonPath = JsonPath.root.a.b
//      val json = x.asJson
//      println(json)
//      val backAgain = decode[JsonPath](json.noSpaces)
//      backAgain shouldBe x
//    }
//  }
}
