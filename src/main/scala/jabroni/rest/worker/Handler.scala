package jabroni.rest.worker

import akka.http.scaladsl.model.HttpResponse
import jabroni.api.worker.DispatchWork
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
  def apply(subscription: Subscription)(onWork: DispatchWork => Future[HttpResponse])(implicit ec: ExecutionContext): Handler = {
    val handle = onWork.andThen { f =>
      f.onComplete { _ =>
        subscription.request(1)
      }
      f
    }
    new Handler(handle)
  }
}
