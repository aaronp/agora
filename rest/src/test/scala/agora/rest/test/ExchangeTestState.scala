package agora.rest.test

import java.io.Closeable

import agora.api.exchange._
import agora.api.worker.SubscriptionKey
import agora.rest.client.RestClient
import agora.rest.exchange.ExchangeConfig._
import agora.rest.exchange.ExchangeServerConfig.RunningExchange
import agora.rest.exchange.{ExchangeClient, ExchangeConfig, ExchangeServerConfig}
import agora.rest.worker.WorkerConfig._
import agora.rest.worker.{WorkerClient, WorkerConfig}
import com.typesafe.scalalogging.StrictLogging

case class ExchangeTestState(server: Option[RunningExchange] = None,
                             exchangeClient: Option[Exchange] = None,
                             submittedJobs: List[(SubmitJob, ClientResponse)] = Nil,
                             workersByName: Map[String, RunningWorker] = Map.empty,
                             lastTakeAck: Option[RequestWorkAck] = None)
    extends ExchangeValidation
    with StrictLogging {
  def verifyTakeAck(subscriptionKey: String, expectedPreviousPending: Int, expectedTotal: Int) = {
    lastTakeAck shouldBe Option(RequestWorkAck(subscriptionKey, expectedPreviousPending, expectedTotal))
    copy(lastTakeAck = None)
  }

  def take(workerName: String, subscriptionKey: SubscriptionKey, items: Int) = {
    val worker: RunningWorker = workersByName(workerName)
    val ack: RequestWorkAck   = worker.service.exchange.take(subscriptionKey, items).futureValue
    copy(lastTakeAck = Option(ack))
  }

  def submitJob(job: SubmitJob): ExchangeTestState = {
    val (state, client) = stateWithClient
    val fut             = client.submit(job)
    val resp            = fut.futureValue
    state.copy(submittedJobs = (job, resp) :: submittedJobs)
  }

  private def stateWithClient: (ExchangeTestState, Exchange) = {
    exchangeClient match {
      case None =>
        val running = server.get
        logger.info(s"Connecting exchange client ${running.conf.clientConfig.location} to ${running.conf.location} (w/ local address ${running.localAddress})")
        val rest: RestClient = running.conf.clientConfig.restClient
        val client = ExchangeClient(rest) { workerLocation =>
          logger.info(s"Creating working client at $workerLocation for exchange on ${running.conf.clientConfig.location}")
          val rest = running.conf.clientConfig.clientFor(workerLocation)
          WorkerClient(rest)
        }
        copy(exchangeClient = Option(client)).stateWithClient
      case Some(c) => this -> c
    }
  }

  def subscriptionQueue: (ExchangeTestState, List[PendingSubscription]) = {
    val (state, client)              = stateWithClient
    val subscriptionsFromClient      = client.queueState().futureValue.subscriptions
    val subscriptionsFromServerState = server.get.service.exchange.queueState().futureValue.subscriptions
    subscriptionsFromClient should contain theSameElementsAs (subscriptionsFromServerState)
    state -> subscriptionsFromServerState
  }

  def jobQueue: (ExchangeTestState, List[SubmitJob]) = {
    val (state, client)     = stateWithClient
    val jobsFromClient      = client.queueState().futureValue.jobs
    val jobsFromServerState = server.get.service.exchange.queueState().futureValue.jobs
    jobsFromClient should contain theSameElementsAs (jobsFromServerState)
    state -> jobsFromServerState
  }

  def close() = {
    workersByName.keySet.foreach(stopWorker)
    closeExchange()
    ExchangeTestState()
  }

  def startWorker(name: String, workerConfig: WorkerConfig): ExchangeTestState = {
    stopWorker(name).copy(workersByName = workersByName.updated(name, workerConfig.startWorker().futureValue))
  }

  def startExchangeServer(exchangeConfig: ExchangeServerConfig): ExchangeTestState = {
    logger.info(s"Starting exchange on ${exchangeConfig.port}")
    closeExchange()
    val running = exchangeConfig.start().futureValue
    running.localAddress.getPort shouldBe exchangeConfig.port
    logger.info(s"Exchange started on ${running.localAddress.getHostString}")
    copy(server = Option(running))
  }

  def stopWorker(name: String): ExchangeTestState = {
    workersByName.get(name).fold(this) { w =>
      w.stop.futureValue
      copy(workersByName = workersByName - name)
    }
  }

  def closeExchange() = {
    server.foreach(_.stop.futureValue)
    exchangeClient.foreach {
      case c: AutoCloseable => c.close()
      case _                =>
    }
  }
}
