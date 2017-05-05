package jabroni.rest.test

import jabroni.api.exchange._
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.{ExchangeClient, ExchangeMain}
import jabroni.rest.ServerConfig
import jabroni.rest.worker.Worker

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

case class ExchangeTestState(
                              server: Option[ExchangeMain.RunningService] = None,
                              exchangeClient: Option[Exchange with QueueObserver] = None,
                              submittedJobs: List[(SubmitJob, SubmitJobResponse)] = Nil,
                              workers: List[Worker.RunningService] = Nil
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

  def startWorker(serverConfig: ServerConfig): ExchangeTestState = {
    stopWorkers(serverConfig)
    copy(workers = Worker.startFromConfig(serverConfig).futureValue :: workers)
  }

  def workerForName(name : String): Worker.RunningService = {
    val found: Option[Worker.RunningService] = workers.find(_.service.workerDetails.right.get.name.exists(_ == name))
    found.get
  }


  def startExchangeServer(serverConfig: ServerConfig): ExchangeTestState = {
    closeExchange()
    copy(server = Option(ExchangeMain.startFromConfig(serverConfig).futureValue))
  }

  def stopWorkers(serverConfig: ServerConfig): ExchangeTestState = {
    val stopFutures = workers.filter(_.conf.location.port == serverConfig.location.port).map { running =>
      running.stop()
    }
    Future.sequence(stopFutures).futureValue
    this
  }

  def closeExchange() = {
    server.foreach(_.stop)
  }
}
