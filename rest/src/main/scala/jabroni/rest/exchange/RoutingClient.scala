package jabroni.rest.exchange

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import io.circe.Json
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{BlockingSubmitJobResponse, _}
import jabroni.api.worker.{WorkerDetails, WorkerRedirectCoords}
import jabroni.rest.client.RestClient

import scala.concurrent.Future


trait RoutingClient {
  self: ExchangeClient =>

  type WorkerResponses = Future[CompletedWork]

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
  def enqueue(submit: SubmitJob): WorkerResponses = sendAndRoute(submit)._2

  import RestClient.implicits._
  import RoutingClient._
//  protected implicit def heresAMaterializer = haveAMaterializer

  private def onSubmitResponse[T: ToEntityMarshaller](request: T, resp: BlockingSubmitJobResponse): WorkerResponses = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    val futures = pears.map {
      case (wdc@WorkerRedirectCoords(location, key, remaining), details) =>
        val matchDetails = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchEpochUTC)
        val wc = clientFor(location)
        val path = details.path.getOrElse(s"Worker ${details} hasn't specified a path")
        wc.dispatch(path, matchDetails, request).map { resp =>
          wdc -> resp
        }
    }

    Future.sequence(futures).map(list => CompletedWork(list)(haveAMaterializer))
  }


  protected def sendAndRoute(submit: SubmitJob): (JobResponse, WorkerResponses) = {
    val httpFuture: Future[HttpResponse] = rest.send(ExchangeHttp(submit))
    implicit val heresAnImplicitMaterializerIFoundForYou = haveAMaterializer
    if (submit.submissionDetails.awaitMatch) {
      val blockRespFuture = httpFuture.flatMap(_.as[BlockingSubmitJobResponse])
      val workerFutures: WorkerResponses = for {
        blockingResp <- blockRespFuture
        workerResponses <- routeMatchedJob(submit, blockingResp)
      } yield {
        workerResponses
      }
      blockRespFuture -> workerFutures
    } else {
      httpFuture.flatMap(_.as[SubmitJobResponse]) -> Future.failed(new Exception(s"awaitMatch was not specified on $submit"))
    }
  }

  def routeMatchedJob(submitJob: SubmitJob, matchResponse: BlockingSubmitJobResponse): WorkerResponses = {
    onSubmitResponse(submitJob.job, matchResponse)
  }

}

object RoutingClient {

  val strM: ToEntityMarshaller[String] = Marshaller.stringMarshaller(MediaTypes.`application/json`)
  implicit val JsonEntityMarshaller: ToEntityMarshaller[Json] = strM.compose((_: Json).noSpaces)

}