package agora.rest
package exchange

import javax.ws.rs.Path

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{entity, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromRequestUnmarshaller}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto.exportEncoder
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import agora.api._
import agora.api.exchange._
import agora.health.HealthDto
import io.swagger.annotations.{ApiOperation, ApiResponse, ApiResponses}

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
case class ExchangeRoutes(exchange: ServerSideExchange)(implicit mat: Materializer) extends FailFastCirceSupport with RouteLoggingSupport {

  import mat.executionContext

  // allow things to watch for matches
  //  val observer: MatchObserver = MatchObserver()
  //  lazy val exchange: Exchange = exchangeForHandler(observer)

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
    def routes: Route = queueState ~ subscriptionsGet ~ jobsGet

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

          exchange.queueState().map(_.subscriptions)
        }
      }
    }

    def jobsGet = get {
      (path("jobs") & pathEnd) {
        complete {
          exchange.queueState().map(_.jobs)
        }
      }
    }
  }

  /**
    * Routes for pushing work (requesting work from) workers
    */
  object submission {
    def routes: Route = submit ~ cancel

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
    @ApiResponses(
      Array(
        new ApiResponse(code = 200, message = "Return Health", response = classOf[HealthDto])
      ))
    def subscribe = put {
      jsonRouteFor[WorkSubscription, WorkSubscriptionAck]("subscribe")(exchange.onSubscriptionRequest)
    }

    def takeNext = post {
      jsonRouteFor[RequestWork, RequestWorkAck]("take")(exchange.onSubscriptionRequest)
    }

    def cancel = delete {
      (path("subscriptions") & pathEnd) {
        import io.circe.generic.auto._

        val dec: Decoder[CancelSubscriptions]                 = implicitly[Decoder[CancelSubscriptions]]
        val feu: FromEntityUnmarshaller[CancelSubscriptions]  = implicitly[FromEntityUnmarshaller[CancelSubscriptions]]
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
