package jabroni.rest.ui

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class SupportRoutes()(implicit ec: ExecutionContext) {

  def routes: Route = pathPrefix("rest") {
    debugRoute
  }

  val debugRoute = (get & pathPrefix("debug")) {
    encodeResponse {
      getFromBrowseableDirectory(".")
    }
  }
}