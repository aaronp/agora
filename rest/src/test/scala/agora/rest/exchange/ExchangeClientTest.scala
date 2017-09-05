package agora.rest.exchange

import agora.api.exchange.{Exchange, ExchangeSpec, MatchObserver}
import agora.rest.exchange.ExchangeServerConfig.RunningExchange
import org.scalatest.BeforeAndAfter
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration._

class ExchangeClientTest extends ExchangeSpec with BeforeAndAfter {

  var runningServer: RunningExchange = null

  var config = ExchangeServerConfig()

  override val supportsObserverNotifications = false

  var client: ExchangeClient = null
  before {
    config = ExchangeServerConfig()
    runningServer = Await.result(config.start(), 5.seconds)
  }

  after {
    runningServer.stop()
    client.close()
  }

  override def newExchange(observer: MatchObserver): Exchange = {
    client = config.client
    client

  }

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(150, Millis)))
}
