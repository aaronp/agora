package agora.rest.exchange

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import io.circe.Json
import agora.api.`match`.MatchDetails
import agora.api.exchange.{BlockingSubmitJobResponse, _}
import agora.api.worker.{WorkerDetails, WorkerRedirectCoords}
import agora.rest.client.RestClient
import agora.rest.worker.WorkerClient

import scala.concurrent.Future

/**
  * Encapsulate the exchange client workflow of:
  * 1) submit a request to the exchange
  * 2) have a response telling it where it should go
  * 3) submitting a request to that redirected location
  * 4) returning 'CompletedWork', which contains the worker(s) and their responses
  */
trait RoutingClient { self: ExchangeClient =>

  type WorkerResponses = Future[CompletedWork]
  type WorkerCallback  = (WorkerClient, WorkerDetails)

  import RestClient.implicits._

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
    *
    * @param submit
    * @param doWork
    * @return
    */
  def enqueueAndDispatch(submit: SubmitJob)(doWork: WorkerClient => Future[HttpResponse]): (JobResponse, WorkerResponses) = {
    sendAndRouteWorkerRequest(submit)(doWork)
  }

  protected def onSubmitResponse(resp: BlockingSubmitJobResponse)(sendToWorker: WorkerClient => Future[HttpResponse]): WorkerResponses = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    val futures = pears.map {
      case (wdc @ WorkerRedirectCoords(location, key, remaining), details) =>
        val matchDetails     = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchEpochUTC)
        val path             = details.path
        val wc: WorkerClient = clientFor(location)(path, matchDetails, details)
        sendToWorker(wc).map { resp =>
          wdc -> resp
        }
    }

    Future.sequence(futures).map(list => CompletedWork(list)(rest.materializer))
  }

  /**
    * Submit the job, then on the (expected) rediret response, route the work to the given worker using the 'sendToWorker'
    *
    * @param submit       the job to submit
    * @param sendToWorker the function used to send work to the worker (which may or may not have been the same request)
    * @return both the original work submission response and the response from the worker
    */
  def sendAndRouteWorkerRequest(submit: SubmitJob)(sendToWorker: WorkerClient => Future[HttpResponse]): (JobResponse, WorkerResponses) = {

    // the submission is requesting that it doesn't receive a response until a match, which means the response
    // will come back as a BlockingSubmitJobResponse
    def submitAsBlockingSubmission: Future[BlockingSubmitJobResponse] = {
      rest.send(ExchangeHttp(submit)).flatMap { resp =>
        resp.as[BlockingSubmitJobResponse](retryOnError(submitAsBlockingSubmission))
      }
    }

    // the job doesn't care (for some reason ... how odd, as it will likely get a redirection response that it then
    // does nothing with. huh.)
    def submitAsAsyncSubmission: Future[SubmitJobResponse] = {
      rest.send(ExchangeHttp(submit)).flatMap(_.as[SubmitJobResponse](retryOnError(submitAsAsyncSubmission)))
    }

    if (submit.submissionDetails.awaitMatch) {
      val blockRespFuture = submitAsBlockingSubmission
      val workerFutures: WorkerResponses = for {
        blockingResp    <- blockRespFuture
        workerResponses <- onSubmitResponse(blockingResp)(sendToWorker)
      } yield {
        workerResponses
      }
      blockRespFuture -> workerFutures
    } else {
      submitAsAsyncSubmission -> Future.failed(new Exception(s"awaitMatch was not specified on $submit"))
    }
  }
}

object RoutingClient {

  private val strM: ToEntityMarshaller[String]                = Marshaller.stringMarshaller(MediaTypes.`application/json`)
  implicit val JsonEntityMarshaller: ToEntityMarshaller[Json] = strM.compose((_: Json).noSpaces)

}
