package agora.rest.exchange

import org.scalatest.{Matchers, WordSpec}

class ExchangeConfigTest extends WordSpec with Matchers {

  "ExchangeConfig()" should {
    "resolve" in {
      ExchangeConfig().host shouldBe "0.0.0.0"
    }
    "reflect the server port in the client port" in {
      val conf = ExchangeConfig("port=1221")

      conf.port shouldBe 1221
      conf.clientConfig.port shouldBe 1221
    }
  }
}
