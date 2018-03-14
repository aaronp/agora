package agora.json

import org.scalatest.{Matchers, WordSpec}

import scala.language.implicitConversions

class JPartTest extends WordSpec with Matchers {

  import io.circe.syntax._
  import AgoraJsonImplicits._

  "JFilter.and" should {
    "return true when both conditions are true" in {
      (("value" lte 7) and ("value" =!= 6)).matches(Map("value" -> 6).asJson) shouldBe (false)
      (("value" lte 7) and ("value" !== 6)).matches(Map("value" -> 5).asJson) shouldBe (true)
    }
  }

  "JFilter.or" should {
    "return true when either condition is true" in {
      (("value" lte 7) or ("value" gte 61)).matches(Map("value" -> 15).asJson) shouldBe (false)
      (("value" lte 7) or ("value" gte 61)).matches(Map("value" -> 65).asJson) shouldBe (true)
    }
  }
  "JFilter.gt" should {
    "value gt 7 should match { value : 8 }" in {
      ("value" gt 7).asMatcher().matches(Map("value" -> 8).asJson) shouldBe (true)
    }
    "value gt 7 should not match { value : 7 }" in {
      ("value" gt 7).asMatcher().matches(Map("value" -> 7).asJson) shouldBe (false)
    }
    "value gt 0 should match { value : \"7\" }" in {
      ("value" gt 0).asMatcher().matches(Map("value" -> "7").asJson) shouldBe (true)
    }
    "value gt 0 should not match { value : \"foo\" }" in {
      ("value" gt 0).asMatcher().matches(Map("value" -> "foo").asJson) shouldBe (false)
    }
  }
  "JFilter.lt" should {
    "value lt 7 should match { value : 6 }" in {
      ("value" lt 7).asMatcher().matches(Map("value" -> 6).asJson) shouldBe (true)
    }
    "value lt 7 should not match { value : 7 }" in {
      ("value" lt 7).asMatcher().matches(Map("value" -> 7).asJson) shouldBe (false)
    }
  }

  "JFilter.lte" should {
    "value lte 7 should match { value : 6 }" in {
      ("value" lte 7).asMatcher().matches(Map("value" -> 6).asJson) shouldBe (true)
    }
    "value lte 7 should match { value : 7 }" in {
      ("value" lte 7).asMatcher().matches(Map("value" -> 7).asJson) shouldBe (true)
    }
  }
  "JFilter.gte" should {
    "value gte 7 should not match { value : 6 }" in {
      ("value" gte 7).asMatcher().matches(Map("value" -> 6).asJson) shouldBe (false)
    }
    "value gte 7 should match { value : 7 }" in {
      ("value" gte 7).asMatcher().matches(Map("value" -> 7).asJson) shouldBe (true)
    }
  }
  "JFilter.~=" should {
    Seq("start and finish", "startfinish").foreach { expected =>
      s"""value ~= ^start.*finish$$ should match $expected""" in {
        ("value" ~= "^start.*finish$")
          .asMatcher()
          .matches(Map("value" -> expected).asJson) shouldBe (true)
      }
    }
    Seq("Startfinish", "will start then finish", "start finish soup").foreach { expected =>
      s"""value ~= ^start.*finish$$ should not match $expected""" in {
        ("value" ~= "^start.*finish$")
          .asMatcher()
          .matches(Map("value" -> expected).asJson) shouldBe (false)
      }
    }
  }
}
