package agora.rest.exchange

import javax.ws.rs.Path

import agora.api.exchange._
import akka.http.scaladsl.server.Directives.{as, complete, entity, get, path, post, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.auto.exportEncoder
import io.swagger.annotations._

import scala.language.reflectiveCalls

trait ExchangeQueryRoutes extends FailFastCirceSupport {

  def exchange: ServerSideExchange

  def queryRoutes: Route = queueState ~ queueStateGet ~ subscriptionsGet ~ jobsGet

  @Path("/rest/exchange/queue")
  @ApiOperation(
    value = "Queries the exchange queue",
    notes = "This should be for support purposes, as job routing is done via work subscriptions and job submissions",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "The queue state", response = classOf[QueueState])
    ))
  def queueStateGet = get {
    (path("queue") & pathEnd) {
      complete {
        exchange.queueState(QueueState())
      }
    }
  }

  @Path("/rest/exchange/queue")
  @ApiOperation(
    value = "Queries the exchange queue",
    notes = "This should be for support purposes, as job routing is done via work subscriptions and job submissions",
    httpMethod = "POST"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "a queue filter",
                           required = true,
                           dataTypeClass = classOf[QueueState],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "The queue state", response = classOf[QueueState])
    ))
  def queueState = post {
    (path("queue") & pathEnd) {
      entity(as[QueueState]) { request =>
        complete {
          exchange.queueState(request)
        }
      }
    }
  }

  private implicit def listSubscriptionEncoder: Encoder[List[PendingSubscription]] =
    exportEncoder[List[PendingSubscription]].instance

  @Path("/rest/exchange/subscriptions")
  @ApiOperation(
    value = "Queries the subscriptions in the exchange queue",
    notes = "This should be for support purposes as a convenience to read the queue subscriptions",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "The queue subscriptions", response = classOf[List[PendingSubscription]])
    ))
  def subscriptionsGet = get {
    extractMaterializer { implicit mat =>
      import mat.executionContext

      (path("subscriptions") & pathEnd) {
        complete {
          exchange.queueState().map(_.subscriptions.toVector)
        }
      }
    }
  }

  @Path("/rest/exchange/jobs")
  @ApiOperation(
    value = "Queries the jobs in the exchange queue",
    notes = "This should be for support purposes as a convenience to read the queued jobs",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "The queue subscriptions", response = classOf[List[SubmitJob]])
    ))
  def jobsGet = get {
    (path("jobs") & pathEnd) {
      extractMaterializer { implicit mat =>
        import mat.executionContext
        complete {
          exchange.queueState().map(_.jobs.toVector)
        }
      }
    }
  }

}
