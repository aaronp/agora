package agora.rest.exchange

import javax.ws.rs.Path

import agora.api.exchange._
import agora.api.nextJobId
import agora.rest.implicits
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, path, put, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto.exportEncoder
import io.circe.syntax._
import io.swagger.annotations._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * Routes for pushing work (requesting work from) workers
  */
trait ExchangeSubmissionRoutes extends FailFastCirceSupport {

  def exchange: ServerSideExchange

  /** @return the /rest/exchange/submit routes used for worker subscriptions
    */
  def submissionRoutes: Route = submit ~ cancelJobs

  @Path("/rest/exchange/submit")
  @ApiOperation(value = "Submits work to be matched with a work subscription", notes = "???", httpMethod = "POST")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "???", required = true, dataTypeClass = classOf[WorkSubscription], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "???", response = classOf[WorkSubscriptionAck])
    ))
  def submit = put {
    (path("submit") & pathEnd) {
      extractMaterializer { implicit mat =>
        import mat.executionContext

        entity(as[SubmitJob]) {
          case submitJob if submitJob.submissionDetails.awaitMatch =>
            complete {
              submitJobAndAwaitMatch(submitJob)
            }
          case submitJob =>
            complete {
              submitJobFireAndForget(submitJob)
            }
        }
      }
    }
  }

  @Path("/rest/exchange/jobs")
  @ApiOperation(value = "Cancels a queued job", notes = "???", httpMethod = "DELETE")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "???", required = true, dataTypeClass = classOf[CancelJobs], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "???", response = classOf[CancelJobsResponse])
    ))
  def cancelJobs = delete {
    (path("jobs") & pathEnd) {
      entity(as[CancelJobs]) { request =>
        complete {
          exchange.cancelJobs(request)
        }
      }
    }
  }

  def submitJobAndAwaitMatch(submitJob: SubmitJob)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val jobWithId                                      = submitJob.jobId.fold(submitJob.withId(nextJobId()))(_ => submitJob)
    val matchFuture: Future[BlockingSubmitJobResponse] = exchange.observer.onJob(jobWithId)
    exchange.submit(jobWithId).flatMap { _ =>
      matchFuture.map { r: BlockingSubmitJobResponse =>
        // TODO - check if the redirection is to US, as we can just process it outselves like

        import implicits._
        HttpResponse(status = StatusCodes.TemporaryRedirect,
                     headers = r.firstWorkerUrl.map("Location".asHeader).toList,
                     entity = HttpEntity(`application/json`, r.asJson.noSpaces))
      }
    }
  }

  def submitJobFireAndForget(submitJob: SubmitJob)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    exchange.submit(submitJob).map {
      case r: SubmitJobResponse => HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
      case other                => sys.error(s"received '${other}' response after submitting a 'await match' job $submitJob")

    }
  }
}
