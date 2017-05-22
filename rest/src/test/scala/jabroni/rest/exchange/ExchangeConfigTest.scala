package jabroni.rest.exchange

import org.scalatest.{Matchers, WordSpec}

import scala.util.Properties

class ExchangeConfigTest extends WordSpec with Matchers {

  "ExchangeConfig()" should {
    "resolve" in {
      ExchangeConfig().runUser shouldBe Properties.userName
    }
  }
  "ExchangeConfig(strings...)" should {
    "resolve local config strings" in {
      ExchangeConfig("runUser=foo").runUser shouldBe "foo"
    }
  }
}
