package jabroni.rest.test

import com.sun.xml.internal.org.jvnet.fastinfoset.sax.RestrictedAlphabetContentHandler
import jabroni.api.exchange._
import jabroni.api.json.JMatcher
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.{ExchangeMain, ServerConfig, WorkerMain}

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

case class ExchangeTestState(
                              server: Option[ExchangeMain.RunningService] = None,
                              exchangeClient: Option[Exchange] = None,
                              submittedJobs: List[(SubmitJob, Future[SubmitJobResponse])] = Nil,
                              workers: List[WorkerMain.RunningService] = Nil
                            )
  extends ExchangeValidation {
  def submitJob(job: SubmitJob): ExchangeTestState = {
    val (state, client) = stateWithClient
    val resp: Future[SubmitJobResponse] = client.submit(job)
    state.copy(submittedJobs = (job, resp) :: submittedJobs)
  }

  private def stateWithClient: (ExchangeTestState, Exchange) = {
    exchangeClient match {
      case None =>
        val loc = server.get.conf.location
        RestrictedAlphabetContentHandler
        val client: Exchange = ExchangeClient(RestClient(loc))
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
    val jobsFromClient = client.listJobs(new QueuedJobs()).futureValue.jobs
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
    copy(workers = WorkerMain.start(serverConfig).futureValue :: workers)
  }


  def startExchangeServer(serverConfig: ServerConfig): ExchangeTestState = {
    closeExchange()
    copy(server = Option(ExchangeMain.start(serverConfig).futureValue))
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
