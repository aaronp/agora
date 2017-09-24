package agora.api.exchange

import agora.api.`match`.MatchDetails
import agora.api.worker.{WorkerDetails, WorkerRedirectCoords}
import io.circe.Encoder

import scala.annotation.implicitNotFound
import scala.concurrent.{ExecutionContext, Future}

/**
  * The ability to create [[SubmitJob]] instances from some value
  *
  * @tparam T the type which will be represented by the [[SubmitJob]]'s 'job' json value
  */
trait Submitable[T] {

  /**
    * @return the input request T into a SubmitJob
    */
  def asSubmitJob(request: T): SubmitJob
}

object Submitable {

  object implicits extends LowPriorityCirceSubmitable

  /**
    * An instance of [[JobSyntax]]
    *
    * @param value      the input job (before being converted/marshalled/pickled to Json)
    * @param submitable evidence that we can convert 'T' values to a [[SubmitJob]]
    * @tparam T the input type which will be converted to the 'job' part of a [[SubmitJob]]
    */
  class AsJob[T](override val value: T, override val submitable: Submitable[T]) extends JobSyntax[T]

  trait LowPriorityCirceSubmitable {

    implicit object SubmitableIdentity extends Submitable[SubmitJob] {
      override def asSubmitJob(request: SubmitJob) = request
    }

    implicit def asSubmitable[T](implicit encoder: Encoder[T], details: SubmissionDetails = SubmissionDetails()): Submitable[T] = {
      new Submitable[T] {
        override def asSubmitJob(request: T) = {
          val asJson = implicitly[Encoder[T]]
          SubmitJob(details, asJson(request))
        }
      }
    }

    implicit def asJobSyntax[T](value: T)(implicit asSubmitable: Submitable[T]) = new AsJob(value, asSubmitable)
  }

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
    * @tparam T the request type
    */
  trait JobSyntax[T] {
    def value: T

    /**
      * @return submitable type witness
      */
    def submitable: Submitable[T]

    def asJob(): SubmitJob = {
      submitable.asSubmitJob(value)
    }

    def asJob(details: SubmissionDetails): SubmitJob = asJob().withDetails(details)

    /**
      * Syntax which allows the [[Exchange]] to be explicitly specified but the [[AsClient]]
      * and execution context to be pulled from explicit scope.
      *
      * @param exchange
      * @param asClient
      * @param ec
      * @tparam Out
      * @return the result of the service dispatched to
      */
    def enqueueIn[Out](exchange: Exchange)(implicit
                                           asClient: AsClient[T, Out],
                                           ec: ExecutionContext): Future[Out] = {
      enqueue(exchange, asClient, ec)
    }

    /**
      * 'enqueue' is a convenience method which will then
      * make a request against the matched worker and return that result.
      *
      * Note: It is assumed that the 'enqueue' is used against a job w/ [[SubmissionDetails]] which specify only a single
      * worker is chosen. If a mode is provided which returns multiple workers, then 'enqueueAll' is your darling.
      *
      * @param exchange the exchange to which this job should be enqueued
      * @tparam Out the result type of the [[AsClient]]
      * @return the result from the single asClient worker
      */
    @implicitNotFound("An implicit exchange to which the request is enqueue must be in scope, as well as a AsClient for T -> Out")
    def enqueue[Out](implicit exchange: Exchange, asClient: AsClient[T, Out], ec: ExecutionContext): Future[Out] = {
      val job = asJob
      if (job.submissionDetails.selection.selectsMultiple) {
        Future.failed(new Exception(s"Selection mode ${job.submissionDetails.selection} may return multiple results for $job -- use 'enqueueAll'"))
      } else {
        enqueueAll[Out](job).map { list =>
          list.ensuring(_.size == 1, s"${list.size} results returned for $job").head
        }
      }
    }

    /**
      * Similar to 'submit', but returns the responses from the worker.
      *
      * Submit the job, then on the (expected) redirect response, route the work to the given worker using the asClient
      *
      * @param exchange the exchange to which this job should be enqueued
      * @tparam Out the response of the [[AsClient]] function used to send work to the worker (which may or may not have been the same request)
      * @return both the original work submission response and the response from the worker
      */
    def enqueueAll[Out](job: SubmitJob = asJob)(implicit
                                                exchange: Exchange,
                                                asClient: AsClient[T, Out],
                                                ec: ExecutionContext): Future[List[Out]] = {
      if (job.submissionDetails.awaitMatch) {
        val blockRespFuture = exchange.submit(job).mapTo[BlockingSubmitJobResponse]
        for {
          blockingResp    <- blockRespFuture
          workerResponses <- onSubmitResponse(job, blockingResp)
        } yield {
          workerResponses
        }
      } else {
        Future.failed(new Exception(s"awaitMatch was not specified on $job"))
      }
    }

    private def onSubmitResponse[Out](job: SubmitJob, resp: BlockingSubmitJobResponse)(implicit
                                                                                       asClient: AsClient[T, Out],
                                                                                       ec: ExecutionContext): Future[List[Out]] = {
      val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
      val futures = pears.map {
        case (WorkerRedirectCoords(_, key, remaining), details) =>
          val matchDetails = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchedAt)
          asClient.dispatch(Dispatch[T](value, job, matchDetails, details))
      }

      Future.sequence(futures)
    }
  }

}
