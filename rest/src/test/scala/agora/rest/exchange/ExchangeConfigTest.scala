package agora.rest.exchange

import org.scalatest.{Matchers, WordSpec}

class ExchangeConfigTest extends WordSpec with Matchers {

  "ExchangeConfig()" should {
    "resolve" in {
      ExchangeConfig().host shouldBe "localhost"
    }
  }
}
