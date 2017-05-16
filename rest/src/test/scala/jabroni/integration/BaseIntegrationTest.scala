package jabroni.integration

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import jabroni.rest.exchange.{ExchangeClient, ExchangeConfig, ExchangeRoutes}
import jabroni.rest.worker.WorkerConfig
import jabroni.rest.worker.WorkerConfig.RunningWorker
import jabroni.rest.{BaseSpec, RunningService}
import org.scalatest.BeforeAndAfterAll

abstract class BaseIntegrationTest extends BaseSpec with BeforeAndAfterAll {

  import BaseIntegrationTest._

  implicit val testSystem = ActorSystem("test")
  implicit val mat = ActorMaterializer()
  implicit val ec = mat.executionContext
  private val exchangePort = portCounter.incrementAndGet()
  private val workerPort = portCounter.incrementAndGet()
  val workerConfig = WorkerConfig(s"port=${workerPort}", s"exchange.port=${exchangePort}")
  lazy val exchangeConfig = ExchangeConfig(s"port=${exchangePort}")
  var exchangeService: RunningService[ExchangeConfig, ExchangeRoutes] = null
  var exchangeClient: ExchangeClient = null
  var worker: RunningWorker = null

  override def beforeAll = {
    super.beforeAll()
    startAll
  }

  def startAll = {
    exchangeService = exchangeConfig.startExchange().futureValue
    exchangeClient = workerConfig.exchangeClient
    worker = workerConfig.startWorker().futureValue
  }

  def stopAll = {
    exchangeService.close()
    exchangeClient.close()
  }

  override def afterAll = {
    super.afterAll()
    stopAll
  }
}

object BaseIntegrationTest {
  private val portCounter = new AtomicInteger(8000)
}