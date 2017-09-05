package agora.integration

import java.util.concurrent.atomic.AtomicInteger

import agora.api.BaseSpec
import agora.rest.exchange.{ExchangeClient, ExchangeServerConfig}
import agora.rest.worker.WorkerConfig
import agora.rest.worker.WorkerConfig.RunningWorker
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

abstract class BaseIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterAll with BeforeAndAfter {

  import BaseIntegrationTest._

  implicit val testSystem                                   = ActorSystem("test")
  implicit val mat                                          = ActorMaterializer()
  implicit val ec                                           = mat.executionContext
  private val exchangePort                                  = portCounter.incrementAndGet()
  private val workerPort                                    = portCounter.incrementAndGet()
  var workerConfig: WorkerConfig                            = null
  lazy val exchangeConfig                                   = ExchangeServerConfig(s"port=${exchangePort}")
  var exchangeService: ExchangeServerConfig.RunningExchange = null
  var exchangeClient: ExchangeClient                        = null
  var worker: RunningWorker                                 = null

  before(startAll)
  after(stopAll)

  def startAll() = {
    workerConfig = WorkerConfig(s"port=${workerPort}", s"exchange.port=${exchangePort}")
    exchangeService = exchangeConfig.start().futureValue
    exchangeClient = workerConfig.exchangeClient
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
