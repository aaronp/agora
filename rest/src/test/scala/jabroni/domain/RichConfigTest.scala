package jabroni.domain

import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import org.scalatest.{Matchers, WordSpec}

class RichConfigTest extends WordSpec with Matchers {

  "RichConfig.intersect" should {
    "compute the intersection of two configs" in {
      val a = ConfigFactory.parseString(
        """ thing : {
          |   b : 2
          |   c : 3
          | }
          | bar : y
        """.stripMargin)
      val b = ConfigFactory.parseString(
        """ thing : {
          |   a : 1
          |   b : 2
          | }
          | foo : x
        """.stripMargin)

      import RichConfig.implicits._
      a.intersect(b).paths shouldBe Set("thing.b")
    }
  }
}
