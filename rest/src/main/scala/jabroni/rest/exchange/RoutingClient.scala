package jabroni.rest.exchange

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import io.circe.Json
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{BlockingSubmitJobResponse, _}
import jabroni.api.worker.{WorkerDetails, WorkerRedirectCoords}
import jabroni.rest.client.RestClient
import jabroni.rest.worker.WorkerClient

import scala.concurrent.Future

/**
  * Encapsulate the exchange client workflow of:
  * 1) submit a request to the exchange
  * 2) have a response telling it where it should go
  * 3) submitting a request to that redirected location
  * 4) returning 'CompletedWork', which contains the worker(s) and their responses
  */
trait RoutingClient {
  self: ExchangeClient =>

  type WorkerResponses = Future[CompletedWork]
  type WorkerCallback = (WorkerClient, WorkerDetails)

  /**
    * Similar to 'submit', but returns the result from the worker.
    *
    * This assumes the job submitted to the exchange can be sent verbatim to the worker.
    *
    * The workflow is:
    *
    * 1) submit to exchange, awaiting a match
    * 2) on (redirect) response (when a worker is eventually assigned), submit the work to the matched worked
    * 3) return the response from the worker ... which could be *anything*
    *
    * @param submit the job submission
    * @return the worker response
    */
  def enqueue(submit: SubmitJob): WorkerResponses = enqueueAndDispatch(submit)(_.sendRequest(submit.job))._2

  /**
    * Enqueues the given job, then uses the supplied function to send the matched work to the workers
    * @param submit
    * @param doWork
    * @return
    */
  def enqueueAndDispatch(submit: SubmitJob)(doWork : WorkerClient => Future[HttpResponse]): (JobResponse, WorkerResponses) = {
    sendAndRouteWorkerRequest(submit)(doWork)
  }

  import RestClient.implicits._
  import RoutingClient._

  private def onSubmitResponse(resp: BlockingSubmitJobResponse)(sendToWorker: WorkerClient => Future[HttpResponse]): WorkerResponses = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    val futures = pears.map {
      case (wdc@WorkerRedirectCoords(location, key, remaining), details) =>
        val matchDetails = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchEpochUTC)
        val path = details.path.getOrElse(s"Worker ${details} hasn't specified a path")
        val wc: WorkerClient = clientFor(location)(path, matchDetails, details)
        sendToWorker(wc).map { resp =>
          wdc -> resp
        }
    }

    Future.sequence(futures).map(list => CompletedWork(list)(haveAMaterializer))
  }

  def sendAndRouteWorkerRequest(submit: SubmitJob)(sendToWorker: WorkerClient => Future[HttpResponse]): (JobResponse, WorkerResponses) = {
    val httpFuture: Future[HttpResponse] = rest.send(ExchangeHttp(submit))
    implicit val heresAnImplicitMaterializerIFoundForYou = haveAMaterializer
    if (submit.submissionDetails.awaitMatch) {
      val blockRespFuture = httpFuture.flatMap(_.as[BlockingSubmitJobResponse])
      val workerFutures: WorkerResponses = for {
        blockingResp <- blockRespFuture
        workerResponses <- onSubmitResponse(blockingResp)(sendToWorker)
      } yield {
        workerResponses
      }
      blockRespFuture -> workerFutures
    } else {
      httpFuture.flatMap(_.as[SubmitJobResponse]) -> Future.failed(new Exception(s"awaitMatch was not specified on $submit"))
    }
  }

}

object RoutingClient {

  val strM: ToEntityMarshaller[String] = Marshaller.stringMarshaller(MediaTypes.`application/json`)
  implicit val JsonEntityMarshaller: ToEntityMarshaller[Json] = strM.compose((_: Json).noSpaces)

}