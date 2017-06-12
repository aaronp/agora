package jabroni.rest.test

import jabroni.api.exchange._
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.ExchangeConfig._
import jabroni.rest.exchange.{ExchangeClient, ExchangeConfig}
import jabroni.rest.worker.{WorkerClient, WorkerConfig}
import jabroni.rest.worker.WorkerConfig._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

case class ExchangeTestState(server: Option[RunningExchange] = None,
                             exchangeClient: Option[Exchange] = None,
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

  private def stateWithClient: (ExchangeTestState, Exchange) = {
    exchangeClient match {
      case None =>
        val config: ExchangeConfig = server.get.conf
        val rest = config.newRestClient(config.location)
        val client = ExchangeClient(rest) { workerLocation =>
          val rest = config.newRestClient(workerLocation)
          WorkerClient(rest)
        }
        copy(exchangeClient = Option(client)).stateWithClient
      case Some(c) => this -> c
    }
  }

  def subscriptionQueue: (ExchangeTestState, List[PendingSubscription]) = {
    val (state, client) = stateWithClient
    val subscriptionsFromClient = client.queueState().futureValue.subscriptions
    val subscriptionsFromServerState = server.get.service.exchange.queueState().futureValue.subscriptions
    subscriptionsFromClient should contain theSameElementsAs (subscriptionsFromServerState)
    state -> subscriptionsFromServerState
  }

  def jobQueue: (ExchangeTestState, List[SubmitJob]) = {
    val (state, client) = stateWithClient
    val jobsFromClient = client.queueState().futureValue.jobs
    val jobsFromServerState = server.get.service.exchange.queueState().futureValue.jobs
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
    copy(server = Option(exchangeConfig.start().futureValue))
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
