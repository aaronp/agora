package jabroni.rest.ui

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class SupportRoutes(config: Config)(implicit ec: ExecutionContext) {

  def routes: Route = pathPrefix("rest") {
    debugRoute ~ getConfig
  }

  val getConfig = (get & pathPrefix("debug" / "config")) {
    complete {
      import jabroni.domain.RichConfig.implicits._

      config.describe
    }
  }
  val debugRoute = (get & pathPrefix("debug")) {
    encodeResponse {
      getFromBrowseableDirectory(".")
    }
  }
}