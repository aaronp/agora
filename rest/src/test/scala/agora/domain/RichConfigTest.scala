package agora.domain

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

class RichConfigTest extends WordSpec with Matchers {

  import RichConfig.implicits._

  "RichConfig.withUserArgs" should {
    "allow the config to set key/value pairs from user arguments" in {
      val conf   = ConfigFactory.parseString("""thing.value = original1
          | hyphenated-key = original2
          | hyphenated-nested.foo : original3
          | value : original4""".stripMargin)
      val actual = conf.withUserArgs(Array("thing.value=new-one", "hyphenated-key=two"))
      actual.collectAsMap shouldBe Map(
        "thing.value"           -> "new-one",
        "hyphenated-key"        -> "two",
        "hyphenated-nested.foo" -> "original3",
        "value"                 -> "original4"
      )
    }

  }
  "RichConfig.collectAsMap" should {
    "collect the string values for a configuration" in {
      val conf = ConfigFactory.parseString(""" thing : {
          |   b : 2
          |   c : 3
          | }
          | bar : true
        """.stripMargin)
      conf.collectAsMap shouldBe Map("thing.b" -> "2", "thing.c" -> "3", "bar" -> "true")
    }
  }
  "RichConfig.intersect" should {
    "compute the intersection of two configs" in {
      val a = ConfigFactory.parseString(""" thing : {
          |   b : 2
          |   c : 3
          | }
          | bar : y
        """.stripMargin)
      val b = ConfigFactory.parseString(""" thing : {
          |   a : 1
          |   b : 2
          | }
          | foo : x
        """.stripMargin)
      a.intersect(b).paths shouldBe List("thing.b")
    }
  }
}
