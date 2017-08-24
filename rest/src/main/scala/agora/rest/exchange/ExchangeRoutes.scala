package agora.rest
package exchange

import javax.ws.rs.Path

import agora.api._
import agora.api.exchange._
import agora.health.HealthDto
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{entity, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromRequestUnmarshaller}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto.exportEncoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import io.swagger.annotations._

import scala.concurrent.Future
import scala.language.reflectiveCalls

/**
  * Handles:
  *
  * PUT rest/exchange/submit
  * PUT rest/exchange/subscribe
  * POST rest/exchange/take
  *
  * POST rest/exchange/subscriptions
  * POST rest/exchange/jobs
  *
  * see ExchangeHttp for the client-side of this
  *
  * @see http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
  */
@Api(value = "Exchange", consumes = "application/json", produces = "application/json")
@Path("/")
case class ExchangeRoutes(exchange: ServerSideExchange)(implicit mat: Materializer) extends FailFastCirceSupport with RouteLoggingSupport {

  import mat.executionContext

  def routes: Route = {
    //encodeResponse
    val all = pathPrefix("rest" / "exchange") {
      worker.routes ~ submission.routes ~ query.routes ~ health
    }
    //logRoute()
    all
  }

  /**
    * Support routes to query the state of the exchange (queues)
    */
  object query {
    def routes: Route = queueState ~ queueStateGet ~ subscriptionsGet ~ jobsGet

    def queueStateGet = get {
      (path("queue") & pathEnd) {
        complete {
          exchange.queueState(QueueState())
        }
      }
    }

    def queueState = post {
      (path("queue") & pathEnd) {
        entity(as[QueueState]) { request =>
          complete {
            exchange.queueState(request)
          }
        }
      }
    }

    private implicit def listSubscriptionEncoder: Encoder[List[PendingSubscription]] = exportEncoder[List[PendingSubscription]].instance

    def subscriptionsGet = get {
      (path("subscriptions") & pathEnd) {
        complete {
          exchange.queueState().map(_.subscriptions.toVector)
        }
      }
    }

    def jobsGet = get {
      (path("jobs") & pathEnd) {
        complete {
          exchange.queueState().map(_.jobs.toVector)
        }
      }
    }
  }

  /**
    * Routes for pushing work (requesting work from) workers
    */
  object submission {
    def routes: Route = submit ~ cancel

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

    def cancel = delete {
      (path("jobs") & pathEnd) {
        entity(as[CancelJobs]) { request =>
          complete {
            exchange.cancelJobs(request)
          }
        }
      }
    }

    def submitJobAndAwaitMatch(submitJob: SubmitJob): Future[HttpResponse] = {
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

    def submitJobFireAndForget(submitJob: SubmitJob): Future[HttpResponse] = {
      exchange.submit(submitJob).map {
        case r: SubmitJobResponse => HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
        case other                => sys.error(s"received '${other}' response after submitting a 'await match' job $submitJob")

      }
    }
  }

  @Path("/rest/exchange/health")
  @ApiOperation(
    value = "A health check endpoint, 'cause you can never have too many of them",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Some health statistics", response = classOf[HealthDto])
    ))
  def health = (get & path("health") & pathEnd) {
    complete {
      HttpResponse(entity = HttpEntity(`application/json`, HealthDto().asJson.noSpaces))
    }
  }

  /**
    * subscription routes called from workers requesting work
    */
  object worker {
    def routes: Route = subscribe ~ takeNext ~ cancel

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
    def cancel = delete {
      (path("subscriptions") & pathEnd) {
        import io.circe.generic.auto._

//        val dec: Decoder[CancelSubscriptions]                 = implicitly[Decoder[CancelSubscriptions]]
//        val feu: FromEntityUnmarshaller[CancelSubscriptions]  = implicitly[FromEntityUnmarshaller[CancelSubscriptions]]
        val fru: FromRequestUnmarshaller[CancelSubscriptions] = implicitly[FromRequestUnmarshaller[CancelSubscriptions]]
        entity(as[CancelSubscriptions](fru)) { request =>
          complete {
            exchange.cancelSubscriptions(request)
          }
        }
      }
    }
  }

  private def jsonRouteFor[T, B](name: String)(handle: T => Future[_ >: B])(implicit um: FromRequestUnmarshaller[T], dec: Decoder[T], enc: Encoder[B]) = {
    (path(name) & pathEnd) {
      entity(as[T]) { input =>
        complete {
          handle(input).map { r =>
            HttpResponse(entity = HttpEntity(`application/json`, enc(r.asInstanceOf[B]).noSpaces))
          }
        }
      }
    }
  }
}
