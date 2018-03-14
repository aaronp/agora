package agora.rest.integration

import java.util.concurrent.atomic.AtomicInteger

import agora.BaseRestSpec
import agora.rest.HasMaterializer
import agora.rest.exchange.{ExchangeRestClient, ExchangeServerConfig}
import agora.rest.worker.WorkerConfig
import agora.rest.worker.WorkerConfig.RunningWorker
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.BeforeAndAfterEach

abstract class BaseIntegrationTest extends BaseRestSpec with FailFastCirceSupport with BeforeAndAfterEach with HasMaterializer {

  import BaseIntegrationTest._

  private val exchangePort                                  = portCounter.incrementAndGet()
  private val workerPort                                    = portCounter.incrementAndGet()
  var workerConfig: WorkerConfig                            = null
  var exchangeConfig: ExchangeServerConfig                  = null
  var exchangeService: ExchangeServerConfig.RunningExchange = null
  var exchangeClient: ExchangeRestClient                    = null
  var worker: RunningWorker                                 = null

  override def beforeEach(): Unit = {
    super.beforeEach()
    startAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    stopAll()
  }

  def startAll() = {
    exchangeConfig = ExchangeServerConfig("host=localhost", s"port=${exchangePort}")
    workerConfig =
      WorkerConfig("host=localhost", "exchange.host=localhost", s"port=${workerPort}", s"exchange.port=${exchangePort}", "includeExchangeRoutes=false")
    exchangeService = exchangeConfig.start().futureValue
    exchangeClient = workerConfig.exchangeClient
    workerConfig.exchangeConfig.port shouldBe exchangeConfig.port
    worker = workerConfig.startWorker().futureValue
  }

  def stopAll() = {
    val exchangeStop = exchangeService.stop()
    val clientStop   = exchangeClient.stop()
    worker.stop().futureValue
    exchangeStop.futureValue
    clientStop.futureValue
  }
}

object BaseIntegrationTest {
  private val portCounter = new AtomicInteger(7000)
}
