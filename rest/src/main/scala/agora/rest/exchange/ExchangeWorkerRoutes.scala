package agora.rest.exchange

import javax.ws.rs.Path

import agora.api.exchange.{ServerSideExchange, _}
import agora.api.worker.WorkerDetails
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{as, complete, delete, entity, path, put, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.swagger.annotations._

import scala.concurrent.Future
import scala.language.reflectiveCalls

/**
  * Contains the routes needed to support workers to the exchange to subscribe/take/cancel work subscriptions
  */
trait ExchangeWorkerRoutes extends FailFastCirceSupport { self: ExchangeRoutes =>

  def exchange: ServerSideExchange

  def workerRoutes: Route = subscribe ~ updateSubscription ~ takeNext ~ cancelSubscriptions

  @Path("/rest/exchange/update/{.*}")
  @ApiOperation(
    value = "Update the work subscription for the given ID with the body of this request",
    notes = "The content of the request is merged with the exiting 'aboutMe' json details",
    httpMethod = "POST"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "the work details to merge", required = true, dataType = "json", paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "the subscription's before and after details", response = classOf[UpdateSubscriptionAck])
    ))
  def updateSubscription = post {
    (path("update" / Segment)) { id =>
      extractMaterializer { implicit mat =>
        import mat.executionContext
        entity(as[Json]) { details =>
          complete {
            val ackFuture: Future[UpdateSubscriptionAck] = exchange.updateSubscriptionDetails(id, WorkerDetails(details))
            ackFuture.map { r =>
              import io.circe.generic.auto._
              import io.circe.syntax._
              HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
            }
          }
        }

      }
    }
  }

  @Path("/rest/exchange/subscribe")
  @ApiOperation(value = "Subscribe for work", notes = "", httpMethod = "PUT")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "the subscription to create", required = true, dataTypeClass = classOf[WorkSubscription], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "An ack for the new subscription", response = classOf[WorkSubscriptionAck])
    ))
  def subscribe = put {
    jsonRouteFor[WorkSubscription, WorkSubscriptionAck]("subscribe")(exchange.onSubscriptionRequest)
  }

  @Path("/rest/exchange/take")
  @ApiOperation(
    value = "Requests (i.e. pulls, takes) more work for the given subscription",
    notes = "If the subscription designated by the request key depends on other subscriptions, it is those subscriptions whose request count will be incremented",
    httpMethod = "POST"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "the subscription from which to request work", required = true, dataTypeClass = classOf[RequestWork], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return an ack which details the previous and current number of pending work items", response = classOf[RequestWorkAck])
    ))
  def takeNext = post {
    jsonRouteFor[RequestWork, RequestWorkAck]("take")(exchange.onSubscriptionRequest)
  }

  @Path("/rest/exchange/subscriptions")
  @ApiOperation(
    value = "Cancels the subscriptions",
    notes = "If the subscription designated by the request key depends on other subscriptions, it is those subscriptions whose request count will be incremented",
    httpMethod = "DELETE"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", value = "the subscriptions to cancel", required = true, dataTypeClass = classOf[CancelSubscriptions], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return an ack which details the previous and current number of pending work items", response = classOf[CancelSubscriptionsResponse])
    ))
  def cancelSubscriptions = delete {
    (path("subscriptions") & pathEnd) {
      import io.circe.generic.auto._

      val fru: FromRequestUnmarshaller[CancelSubscriptions] = implicitly[FromRequestUnmarshaller[CancelSubscriptions]]
      entity(as[CancelSubscriptions](fru)) { request =>
        complete {
          exchange.cancelSubscriptions(request)
        }
      }
    }
  }

}
