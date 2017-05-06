package jabroni.rest.exchange

import org.scalatest.{Matchers, WordSpec}

import scala.util.Properties

class ExchangeConfigTest extends WordSpec with Matchers {

  "ExchangeConfig()" should {
    "resolve" in {
      ExchangeConfig().runUser shouldBe Properties.userName
    }
  }
}
