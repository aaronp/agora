package jabroni.rest.server.routes

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.Json
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class WorkerRoutes()(implicit ec: ExecutionContext) {

  // http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html

  def routes: Route = pathPrefix("rest" / "worker") {
    requestWorkRoute
  }

  private def asResponse(json: Json) = HttpEntity(`application/json`, json.noSpaces)

  val requestWorkRoute = (put & path("request") & pathEnd) {
//    entity(as[RequestWork]) { order =>
//      complete {
//
//      }
//    }
    ???
  }

  val debugRoute = (get & pathPrefix("debug")) {
    encodeResponse {
      getFromBrowseableDirectory(".")
    }
  }


}