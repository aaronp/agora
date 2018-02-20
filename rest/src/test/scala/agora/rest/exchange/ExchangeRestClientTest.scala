package agora.rest.exchange

import agora.api.exchange.observer.ExchangeObserver
import agora.api.exchange.{Exchange, ExchangeSpec}
import agora.rest.exchange.ExchangeServerConfig.RunningExchange
import org.scalatest.BeforeAndAfter
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration._

class ExchangeRestClientTest extends ExchangeSpec with BeforeAndAfter {

  var runningServer: RunningExchange = null

  var config: ExchangeServerConfig = null

  var client: ExchangeRestClient = null

  before {
    config = ExchangeServerConfig("port=6666")
    runningServer = Await.result(config.start(), testTimeout)
  }

  after {
    runningServer.stop().futureValue

    if (client != null) {
      client.stop().futureValue
    }
  }

  override def newExchange(observer: ExchangeObserver): Exchange = {
    client = config.client
    config.connectObserver(observer)
    client
  }

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(150, Millis)))
}
