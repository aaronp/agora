package jabroni.rest.exchange


import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.syntax._
import jabroni.api._
import jabroni.api.exchange.Exchange._
import jabroni.api.exchange._
import jabroni.health.HealthDto

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * Handles:
  *
  * PUT rest/exchange/submit
  * PUT rest/exchange/subscribe
  * POST rest/exchange/take
  *
  * @see http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
  * @param exchangeForHandler
  * @param ec
  */
case class ExchangeRoutes(exchangeForHandler: OnMatch[Unit] => Exchange = Exchange.apply(_)())(implicit ec: ExecutionContext) extends FailFastCirceSupport {

  // allow things to watch for matches
  val observer: MatchObserver = MatchObserver()
  lazy val exchange = exchangeForHandler(observer)

  def routes: Route = pathPrefix("rest" / "exchange") {
    worker.routes ~ client.routes ~ health
  }

  object client {
    def routes: Route = submit

    def submit = put {
      (path("submit") & pathEnd) {
        entity(as[SubmitJob]) {
          case submitJob if submitJob.submissionDetails.awaitMatch =>
            complete {
              val jobWithId = submitJob.withId(nextJobId())
              val matchFuture = observer.onJob(jobWithId)
              exchange.submit(jobWithId).flatMap { _ =>
                matchFuture.map { r =>
                  HttpResponse(entity = HttpEntity(`application/json`, r.asJson.noSpaces))
                }
              }
            }
          case submitJob =>
            complete {
              exchange.submit(submitJob).map { r =>
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

  //
  //  private def asResponse[T](r: T)(implicit enc: Encoder[_ <: T]) = {
  //    HttpResponse(entity = HttpEntity(`application/json`, enc(r).noSpaces))
  //  }
}