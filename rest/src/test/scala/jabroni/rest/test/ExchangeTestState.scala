package jabroni.rest.test

import jabroni.api.exchange._
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.ExchangeConfig._
import jabroni.rest.exchange.{ExchangeClient, ExchangeConfig}
import jabroni.rest.worker.WorkerConfig
import jabroni.rest.worker.WorkerConfig._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

case class ExchangeTestState(server: Option[RunningExchange] = None,
                             exchangeClient: Option[Exchange with QueueObserver] = None,
                             submittedJobs: List[(SubmitJob, ClientResponse)] = Nil,
                             workers: List[RunningWorker] = Nil
                            )
  extends ExchangeValidation {
  def submitJob(job: SubmitJob): ExchangeTestState = {
    val (state, client) = stateWithClient
    val fut = client.submit(job)
    val resp = fut.futureValue
    state.copy(submittedJobs = (job, resp) :: submittedJobs)
  }

  private def stateWithClient: (ExchangeTestState, Exchange with QueueObserver) = {
    exchangeClient match {
      case None =>
        val config = server.get.conf
        import config.implicits._
        val client = ExchangeClient(RestClient(config.location))
        copy(exchangeClient = Option(client)).stateWithClient
      case Some(c) => this -> c
    }
  }

  def subscriptionQueue: (ExchangeTestState, List[PendingSubscription]) = {
    val (state, client) = stateWithClient
    val subscriptionsFromClient = client.listSubscriptions().futureValue.subscriptions
    val subscriptionsFromServerState = server.get.service.exchange.listSubscriptions().futureValue.subscriptions
    subscriptionsFromClient should contain theSameElementsAs (subscriptionsFromServerState)
    state -> subscriptionsFromServerState
  }

  def jobQueue: (ExchangeTestState, List[SubmitJob]) = {
    val (state, client) = stateWithClient
    val jobsFromClient = client.listJobs().futureValue.jobs
    val jobsFromServerState = server.get.service.exchange.listJobs().futureValue.jobs
    jobsFromClient should contain theSameElementsAs (jobsFromServerState)
    state -> jobsFromServerState
  }

  def close() = {
    workers.foreach(_.stop())
    closeExchange()
    ExchangeTestState()
  }

  def startWorker(workerConfig: WorkerConfig): ExchangeTestState = {
    stopWorkers(workerConfig)
    copy(workers = workerConfig.startWorker().futureValue :: workers)
  }

  def workerForName(name: String): RunningWorker = {
    val found: Option[RunningWorker] = workers.find(_.conf.workerDetails.name.exists(_ == name))
    found.get
  }


  def startExchangeServer(exchangeConfig: ExchangeConfig): ExchangeTestState = {
    closeExchange()
    copy(server = Option(exchangeConfig.startExchange().futureValue))
  }

  def stopWorkers(workerConfig: WorkerConfig): ExchangeTestState = {
    val stopFutures = workers.filter(_.conf.location.port == workerConfig.location.port).map { running =>
      running.stop()
    }
    Future.sequence(stopFutures).futureValue
    this
  }

  def closeExchange() = {
    server.foreach(_.stop)
  }
}
