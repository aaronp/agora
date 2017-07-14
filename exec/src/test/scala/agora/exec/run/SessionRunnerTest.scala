package agora.exec.run

import agora.rest.BaseSpec
import agora.rest.exchange.ExchangeConfig
import agora.rest.exchange.ExchangeConfig.RunningExchange

class SessionRunnerTest extends BaseSpec {

  var exchange: RunningExchange = null

  override def beforeAll(): Unit = {
    super.beforeAll()
    exchange = ExchangeConfig.load().start().futureValue
  }

  override def afterAll(): Unit = {
    super.afterAll()
    exchange.stop()
  }

  "SessionRunner" should {
    "be able to upload a file and then execute a commands against that file" in {}
    "be able to execute a commands against that file which is uploaded after the exec is submitted" in {}
    "not be able to execute operation for a file which doesn't exist" in {}
  }
}
