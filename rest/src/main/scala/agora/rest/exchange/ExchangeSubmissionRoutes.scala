package agora.rest.exchange

import javax.ws.rs.Path

import agora.api.exchange._
import agora.rest.implicits._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, path, put, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto.exportEncoder
import io.circe.syntax._
import io.swagger.annotations._

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
  @ApiOperation(value = "Submits work to be matched with a work subscription",
                notes = "If awaitMatch is specified, a ",
                httpMethod = "POST")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "the job to pair against a work subscription",
                           required = true,
                           dataTypeClass = classOf[SubmitJob],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200,
                      message = "returned if the job specifies to awaitMatch",
                      response = classOf[BlockingSubmitJobResponse]),
      new ApiResponse(code = 200,
                      message = "an immediate ack on job receipt when awaitMatch is false",
                      response = classOf[SubmitJobResponse])
    ))
  def submit = put {
    (path("submit") & pathEnd) {
      entity(as[SubmitJob]) { submitJob =>
        extractExecutionContext { implicit ec =>
          complete {
            exchange.submit(submitJob).map {
              case r: SubmitJobResponse         => HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
              case r: BlockingSubmitJobResponse =>
                // TODO - check if the redirection is to US, as we can just process it ourselves like
                HttpResponse(status = StatusCodes.TemporaryRedirect,
                             headers = r.firstWorkerUrl.map("Location".asHeader).toList,
                             entity = HttpEntity(`application/json`, r.asJson.noSpaces))
              case other => sys.error(s"received '${other}' response for $submitJob")
            }
          }
        }
      }
    }
  }

  @Path("/rest/exchange/jobs")
  @ApiOperation(value = "Cancels a queued job", httpMethod = "DELETE")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "an array of job ids to cancel",
                           required = true,
                           dataTypeClass = classOf[CancelJobs],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200,
                      message = "returns a map of the input job ids to their success flags",
                      response = classOf[CancelJobsResponse])
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

}
