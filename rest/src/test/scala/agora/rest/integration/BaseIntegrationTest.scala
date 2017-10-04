package agora.rest.integration

import java.util.concurrent.atomic.AtomicInteger

import agora.BaseSpec
import agora.rest.HasMaterializer
import agora.rest.exchange.{ExchangeRestClient, ExchangeServerConfig}
import agora.rest.worker.WorkerConfig
import agora.rest.worker.WorkerConfig.RunningWorker
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.BeforeAndAfterEach

abstract class BaseIntegrationTest
    extends BaseSpec
    with FailFastCirceSupport
    with BeforeAndAfterEach
    with HasMaterializer {

  import BaseIntegrationTest._

  private def actorConf = ConfigFactory.load("test-system").ensuring(_.getBoolean("akka.daemonic"))

  private val exchangePort                                  = portCounter.incrementAndGet()
  private val workerPort                                    = portCounter.incrementAndGet()
  var workerConfig: WorkerConfig                            = null
  lazy val exchangeConfig                                   = ExchangeServerConfig(s"port=${exchangePort}")
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
    workerConfig = WorkerConfig(s"port=${workerPort}", s"exchange.port=${exchangePort}", "includeExchangeRoutes=false")
    exchangeService = exchangeConfig.start().futureValue
    exchangeClient = workerConfig.exchangeClient
    workerConfig.exchangeConfig.port shouldBe exchangeConfig.port
    worker = workerConfig.startWorker().futureValue
  }

  def stopAll() = {
    exchangeService.close()
    exchangeClient.close()
    worker.close()
  }
}

object BaseIntegrationTest {
  private val portCounter = new AtomicInteger(7000)
}
