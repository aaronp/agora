package agora.rest.exchange

import agora.BaseRestSpec

class ExchangeConfigTest extends BaseRestSpec {

  "ExchangeConfig()" should {
    "reflect the server host in the client host" in {
      val conf = ExchangeConfig("host=foo")
      conf.host shouldBe "foo"
      conf.clientConfig.host shouldBe "foo"
    }
    "reflect the server port in the client port" in {
      val conf = ExchangeConfig("port=1221")

      conf.port shouldBe 1221
      conf.clientConfig.port shouldBe 1221
    }
  }
}
