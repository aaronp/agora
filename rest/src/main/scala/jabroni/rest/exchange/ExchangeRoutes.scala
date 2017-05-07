package jabroni.rest
package exchange


import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{entity, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import jabroni.api._
import jabroni.api.exchange.Exchange._
import jabroni.api.exchange._
import jabroni.health.HealthDto
import jabroni.rest.LoggingSupport

import scala.concurrent.{ExecutionContext, Future}
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
case class ExchangeRoutes(exchangeForHandler: OnMatch[Unit] => Exchange with QueueObserver = Exchange.apply(_)())(implicit mat: Materializer)
  extends FailFastCirceSupport
    with LoggingSupport {

  import mat._

  // allow things to watch for matches
  val observer: MatchObserver = MatchObserver()
  lazy val exchange: Exchange with QueueObserver = exchangeForHandler(observer)

  def routes: Route = {
    val all = pathPrefix("rest" / "exchange") {
      worker.routes ~ publish.routes ~ query.routes ~ health
    }
    logRoute(all)
  }

  /**
    * Support routes to query the state of the exchange (queues)
    */
  object query {
    def routes: Route = subscriptions ~ jobs

    def subscriptions = post {
      (path("subscriptions") & pathEnd) {
        entity(as[ListSubscriptions]) { request =>
          complete {
            exchange.listSubscriptions(request)
          }
        }
      }
    }

    def jobs = post {
      (path("jobs") & pathEnd) {
        entity(as[QueuedJobs]) { request =>
          complete {
            exchange.listJobs(request)
          }
        }
      }
    }
  }

  /**
    * Routes for pushing work (requesting work from) workers
    */
  object publish {
    def routes: Route = submit

    def submit = put {
      (path("submit") & pathEnd) {
        entity(as[SubmitJob]) {
          case submitJob if submitJob.submissionDetails.awaitMatch =>
            complete {
              val jobWithId = submitJob.jobId.fold(submitJob.withId(nextJobId()))(_ => submitJob)
              val matchFuture: Future[BlockingSubmitJobResponse] = observer.onJob(jobWithId)
              exchange.submit(jobWithId).flatMap { _ =>
                matchFuture.map { r:  BlockingSubmitJobResponse =>
                  import implicits._
                  HttpResponse(
                    status = StatusCodes.TemporaryRedirect,
                    headers = r.firstWorkerUrl.map("Location".asHeader).toList,
                    entity = HttpEntity(`application/json`, r.asJson.noSpaces))
                }
              }
            }
          case submitJob =>
            complete {
              exchange.submit(submitJob).map {
                case r: SubmitJobResponse =>
                  HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
              }
            }
        }
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
    def routes: Route = subscribe ~ takeNext

    def subscribe = put {
      jsonRouteFor[WorkSubscription, WorkSubscriptionAck]("subscribe")(exchange.pull)
    }

    def takeNext = post {
      jsonRouteFor[RequestWork, RequestWorkAck]("take")(exchange.pull)
    }
  }

  private def jsonRouteFor[T, B](name: String)(handle: T => Future[_ >: B])(implicit um: FromRequestUnmarshaller[T], dec: Decoder[T], enc: Encoder[B]) = {
    (path(name) & pathEnd) {
      entity(as[T]) {
        input =>
          complete {
            handle(input).map {
              r =>
                HttpResponse(entity = HttpEntity(`application/json`, enc(r.asInstanceOf[B]).noSpaces))
            }
          }
      }
    }
  }
}