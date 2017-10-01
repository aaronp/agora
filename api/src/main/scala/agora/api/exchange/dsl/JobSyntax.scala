package agora.api.exchange.dsl

import agora.api.`match`.MatchDetails
import agora.api.exchange._
import agora.api.worker.{WorkerDetails, WorkerRedirectCoords}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}

import scala.compat.Platform
import scala.concurrent.{ExecutionContext, Future, Promise}

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
trait JobSyntax extends StrictLogging {

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
    *
    * @return the single result
    */
  def enqueue[T, Out](request: T, overrideDetails: SubmissionDetails)(implicit submitable: Submitable[T],
                                                                      asClient: AsClient[T, Out],
                                                                      ec: ExecutionContext): Future[Out] = {
    val job: SubmitJob = submitable.asSubmitJob(request)
    enqueueSingleJob(request, job.copy(submissionDetails = overrideDetails))
  }

  /**
    * Syntax for submitting a job which will either return the first completed or
    */
  def enqueueSingleJob[T, Out](request: T, job: SubmitJob)(implicit asClient: AsClient[T, Out],
                                                           ec: ExecutionContext): Future[Out] = {

    val started                   = Platform.currentTime
    @volatile var firstResultTook = -1L
    val promise                   = Promise[Out]()
    job.submissionDetails.selection match {
      case _: SelectionFirst =>
        // submit the job
        val submissionFuture: Future[List[Future[Out]]] = enqueueJob[T, Out](request, job)
        submissionFuture.foreach { submissions =>
          submissions.foreach { result =>
            result.onComplete { tri =>
              val completed = promise.tryComplete(tri)
              val took      = Platform.currentTime - started
              if (completed) {
                firstResultTook = took
                logger.info(s"First result completed after ${took}ms for $request")
              } else {
                logger.info(
                  s"A result was already returned after ${firstResultTook} ms, ignoring this result after ${took}ms for $request")
              }
            }
          }
        }
      case other if other.selectsMultiple =>
        promise.failure(
          new Exception(
            s"An invalid selection mode for $request was given as part of the submission details, " +
              s"as ${job.submissionDetails.selection}  may return multiple results for $job"))
      case _ =>
        val future: Future[List[Future[Out]]] = enqueueJob[T, Out](request, job)
        val result = future.flatMap { list =>
          list.ensuring(_.size == 1, s"${list.size} results returned for $job").head
        }
        promise.tryCompleteWith(result)
    }
    promise.future
  }

  /**
    * Similar to 'submit', but returns the responses from the worker.
    *
    * Submit the job, then on the (expected) redirect response, route the work to the given worker using the asClient
    *
    * @tparam Out the response of the [[AsClient]] function used to send work to the worker (which may or may not have been the same request)
    * @return both the original work submission response and the response from the worker
    */
  def enqueueAll[T, Out](
      request: T)(implicit submitable: Submitable[T], asClient: AsClient[T, Out], ec: ExecutionContext) = {
    val job = Submitable.instance[T].asSubmitJob(request)
    enqueueJob(request, job)
  }

  /**
    * eventually we'll get a collection of all the response futures (hence Future[List[Future]])
    *
    * @return an eventual collection of all the response futures
    */
  def enqueueJob[T, Out](value: T, job: SubmitJob)(implicit asClient: AsClient[T, Out],
                                                   ec: ExecutionContext): Future[List[Future[Out]]] = {
    if (job.submissionDetails.awaitMatch) {
      val blockRespFuture: Future[BlockingSubmitJobResponse] = exchange.submit(job).mapTo[BlockingSubmitJobResponse]
      blockRespFuture.map { blockingResp =>
        onSubmitResponse[T, Out](value, job, blockingResp)
      }
    } else {
      Future.failed(new Exception(s"awaitMatch was not specified on $job"))
    }
  }

  private def onSubmitResponse[T, Out](value: T, job: SubmitJob, resp: BlockingSubmitJobResponse)(
      implicit
      asClient: AsClient[T, Out],
      ec: ExecutionContext): List[Future[Out]] = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    pears.map {
      case (WorkerRedirectCoords(_, key, remaining), details) =>
        val matchDetails    = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchedAt)
        val dispatchRequest = Dispatch[T](value, job, matchDetails, details)
        asClient.dispatch(dispatchRequest)
    }
  }
}
