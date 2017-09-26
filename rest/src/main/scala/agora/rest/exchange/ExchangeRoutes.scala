package agora.rest
package exchange

import javax.ws.rs.Path

import agora.api.exchange._
import agora.api.health.HealthDto
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import io.circe.syntax._
import io.circe.generic.auto._
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
case class ExchangeRoutes(exchange: ServerSideExchange)
    extends ExchangeSubmissionRoutes
    with ExchangeWorkerRoutes
    with ExchangeQueryRoutes
    with RouteLoggingSupport {

  import ExchangeRoutes._

  def routes: Route = {
    //encodeResponse
    val all = pathPrefix("rest" / "exchange") {
      workerRoutes ~ submissionRoutes ~ queryRoutes ~ health
    }
    //logRoute()
    all
  }

  /**
    * Support routes to query the state of the exchange (queues)
    */

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
  protected def jsonRouteFor[T, B](name: String)(
      handle: T => Future[_ >: B])(implicit um: FromRequestUnmarshaller[T], dec: Decoder[T], enc: Encoder[B]) = {
    (path(name) & pathEnd) {
      extractMaterializer { implicit mat =>
        import mat.executionContext

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
}
