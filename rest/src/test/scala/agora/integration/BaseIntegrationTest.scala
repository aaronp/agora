package agora.integration

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import agora.rest.exchange.{ExchangeClient, ExchangeConfig, ExchangeRoutes}
import agora.rest.worker.WorkerConfig
import agora.rest.worker.WorkerConfig.RunningWorker
import agora.rest.{BaseSpec, RunningService}
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

abstract class BaseIntegrationTest extends BaseSpec with BeforeAndAfterAll with BeforeAndAfter {

  import BaseIntegrationTest._

  implicit val testSystem                                             = ActorSystem("test")
  implicit val mat                                                    = ActorMaterializer()
  implicit val ec                                                     = mat.executionContext
  private val exchangePort                                            = portCounter.incrementAndGet()
  private val workerPort                                              = portCounter.incrementAndGet()
  var workerConfig: WorkerConfig                                      = null
  lazy val exchangeConfig                                             = ExchangeConfig(s"port=${exchangePort}")
  var exchangeService: RunningService[ExchangeConfig, ExchangeRoutes] = null
  var exchangeClient: ExchangeClient                                  = null
  var worker: RunningWorker                                           = null

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
