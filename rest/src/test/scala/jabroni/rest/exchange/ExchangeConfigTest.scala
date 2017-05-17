package jabroni.rest.exchange

import com.typesafe.config.ConfigFactory
import org.scalatest.{Matchers, WordSpec}

import scala.util.Properties

class ExchangeConfigTest extends WordSpec with Matchers {

  "ExchangeConfig()" should {
    "resolve" in {
      ExchangeConfig().runUser shouldBe Properties.userName
    }
  }
  "ConfigFactory.load()" should {
    "load jabroni settings" in {
      val ec = ExchangeConfig(ConfigFactory.load().getConfig("jabroni"))

      ec.runUser shouldBe Properties.userName
    }
  }
}
