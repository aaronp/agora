package jabroni.rest.exchange


import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import jabroni.api.client.{SubmitJob, SubmitJobResponse}
import jabroni.api.exchange._

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
  * @param exchange
  * @param ec
  */
case class ExchangeRoutes(exchange: Exchange)(implicit ec: ExecutionContext) extends FailFastCirceSupport {

  def routes: Route = pathPrefix("rest" / "exchange") {
    worker.routes ~ client.routes ~ health
  }

  object client {
    def routes: Route = submit

    def submit = put {
      jsonRouteFor[SubmitJob, SubmitJobResponse]("submit")(exchange.send)
    }
  }

  def health = (get & path("health") & pathEnd) {
    complete {
      "meh"
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

  private def jsonRouteFor[T, B](name: String)(handle: T => Future[_ >: B])(implicit um: FromRequestUnmarshaller[T], dec : Decoder[T], enc: Encoder[B]) = {
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

  //
  //  private def asResponse[T](r: T)(implicit enc: Encoder[_ <: T]) = {
  //    HttpResponse(entity = HttpEntity(`application/json`, enc(r).noSpaces))
  //  }
}