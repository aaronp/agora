package jabroni.rest.worker

import akka.http.scaladsl.model.HttpResponse
import jabroni.api.exchange.{Exchange, SubmitJobResponse, WorkSubscription}
import jabroni.api.worker.{DispatchWork, SubscriptionKey}
import jabroni.api.worker.execute.RunProcess
import org.reactivestreams.Subscription

import scala.concurrent.{ExecutionContext, Future}

/**
  * We need:
  *
  * a work handler:
  * PUT rest/worker/:name
  *
  * PUT rest/worker/:name/upload // a multipart form of the above
  *
  * nice to have:
  *
  * GET /rest/worker/:jobId/details  // job details
  * GET /rest/worker/:jobId          // cached job response
  */
case class Handler(onWork: DispatchWork => Future[HttpResponse])

object Handler {
  def apply(subscription: Exchange, key: SubscriptionKey)(onWork: DispatchWork => Future[HttpResponse])(implicit ec: ExecutionContext): Handler = {
    val handle = onWork.andThen { f =>
      f.onComplete { _ =>
        subscription.take(key, 1)
      }
      f
    }
    new Handler(handle)
  }

  def resubmitJob(exchange: Exchange, key: SubscriptionKey, req: DispatchWork): Future[SubmitJobResponse] = {
    import jabroni.api.Implicits._
    val original = req.job.submissionDetails.workMatcher
    val newMatcher = original.and("subscription" =!= key.toString)
    val newDetails = req.job.submissionDetails.copy(awaitMatch = false, workMatcher = newMatcher)
    exchange.submit(req.job.copy(submissionDetails = newDetails))
  }

  def webSocketRequest(exchange: Exchange, key: SubscriptionKey): Handler = {
    ???

  }

  def execute(exchange: Exchange, key: SubscriptionKey, runner: RunProcess = RunProcess()): Handler = {
    import runner.ec
    Handler(exchange, key) { req: DispatchWork =>
      runner(req) match {
        case None =>
          resubmitJob(exchange, key, req).map { resp =>
            WorkerHttp.execute.failed
          }
        case Some(future) =>
          future.map { lines =>
            WorkerHttp.execute.response(lines)
          }
      }
    }
  }
}
