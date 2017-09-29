package agora.api.exchange.dsl

import agora.api.`match`.MatchDetails
import agora.api.exchange._
import agora.api.worker.{WorkerDetails, WorkerRedirectCoords}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Exposes 'enqueue' and 'enqueueList' as a means to submit requests via an [[Exchange]].
  *
  * This is true for:
  *
  * 1) requests of type 'T' which have a [[Submitable]] type witness
  * (the 'T' can produce a SubmitJob, which should be true for any T which can
  * be converted to json)
  *
  * 2) there is a [[AsClient]] type witness for the request 'T',
  * which just means something can take a request 'T' and produce a Future[Out] (for any type Out).
  *
  */
trait JobSyntax {

  protected def exchange: Exchange

  /**
    * 'enqueue' is a convenience method which will then
    * make a request against the matched worker and return that result.
    *
    * Note: It is assumed that the 'enqueue' is used against a job w/ [[SubmissionDetails]] which specify only a single
    * worker is chosen. If a mode is provided which returns multiple workers, then 'enqueueAll' is your darling.
    *
    * @tparam Out the result type of the [[AsClient]]
    * @return the result from the single asClient worker
    */
  def enqueue[T, Out](request: T)(implicit submitable: Submitable[T],
                                  asClient: AsClient[T, Out],
                                  ec: ExecutionContext): Future[Out] = {
    val job: SubmitJob = submitable.asSubmitJob(request)
    enqueueSingleJob(request, job)
  }

  /**
    * Enqueues a job w/ the overriding details (instead of the ones from the submitting in scope)
    * @return the single result
    */
  def enqueue[T, Out](request: T, overrideDetails: SubmissionDetails)(implicit submitable: Submitable[T],
                                                                      asClient: AsClient[T, Out],
                                                                      ec: ExecutionContext): Future[Out] = {
    val job: SubmitJob = submitable.asSubmitJob(request)
    enqueueSingleJob(request, job.copy(submissionDetails = overrideDetails))
  }

  def enqueueSingleJob[T, Out](request: T, job: SubmitJob)(implicit asClient: AsClient[T, Out],
                                                           ec: ExecutionContext): Future[Out] = {
    if (job.submissionDetails.selection.selectsMultiple) {
      Future.failed(new Exception(
        s"Selection mode ${job.submissionDetails.selection} may return multiple results for $job -- use 'enqueueAll'"))
    } else {
      enqueueJob[T, Out](request, job).map { list =>
        list.ensuring(_.size == 1, s"${list.size} results returned for $job").head
      }
    }
  }

  /**
    * Similar to 'submit', but returns the responses from the worker.
    *
    * Submit the job, then on the (expected) redirect response, route the work to the given worker using the asClient
    *
    * @tparam Out the response of the [[AsClient]] function used to send work to the worker (which may or may not have been the same request)
    * @return both the original work submission response and the response from the worker
    */
  def enqueueAll[T, Out](request: T)(implicit submitable: Submitable[T],
                                     asClient: AsClient[T, Out],
                                     ec: ExecutionContext): Future[List[Out]] = {
    val job = Submitable.instance[T].asSubmitJob(request)
    enqueueJob(request, job)
  }

  def enqueueJob[T, Out](value: T, job: SubmitJob)(implicit asClient: AsClient[T, Out],
                                                   ec: ExecutionContext): Future[List[Out]] = {
    if (job.submissionDetails.awaitMatch) {
      val blockRespFuture = exchange.submit(job).mapTo[BlockingSubmitJobResponse]
      for {
        blockingResp    <- blockRespFuture
        workerResponses <- onSubmitResponse(value, job, blockingResp)
      } yield {
        workerResponses
      }
    } else {
      Future.failed(new Exception(s"awaitMatch was not specified on $job"))
    }
  }

  private def onSubmitResponse[T, Out](value: T, job: SubmitJob, resp: BlockingSubmitJobResponse)(
      implicit
      asClient: AsClient[T, Out],
      ec: ExecutionContext): Future[List[Out]] = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    val futures = pears.map {
      case (WorkerRedirectCoords(_, key, remaining), details) =>
        val matchDetails    = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchedAt)
        val dispatchRequest = Dispatch[T](value, job, matchDetails, details)
        asClient.dispatch(dispatchRequest)
    }

    Future.sequence(futures)
  }
}
