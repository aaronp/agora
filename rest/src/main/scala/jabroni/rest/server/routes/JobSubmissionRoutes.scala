package jabroni.rest.server.routes

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.generic.auto._
import jabroni.api.RequestWork
import jabroni.json.JsonSupport

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class JobSubmissionRoutes()(implicit ec: ExecutionContext) extends JsonSupport {

  // http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html

  def routes: Route = pathPrefix("rest") {
    requestWorkRoute
  }

  private def asResponse(json: Json) = HttpEntity(`application/json`, json.noSpaces)

  val requestWorkRoute = (put & path("worker" / "request") & pathEnd) {
    entity(as[RequestWork]) { order =>
      complete {

      }
    }
    ???
  }

  val debugRoute = (get & pathPrefix("debug")) {
    encodeResponse {
      getFromBrowseableDirectory(".")
    }
  }


}