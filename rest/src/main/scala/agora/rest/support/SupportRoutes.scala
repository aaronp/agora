package agora.rest.support

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class SupportRoutes(config: Config) {

  def routes: Route = pathPrefix("rest") {
    debugRoute ~ getConfig
  }

  val getConfig = (get & pathPrefix("debug" / "config")) {
    complete {
      import agora.domain.RichConfig.implicits._
      HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, config.json))
    }
  }
  val debugRoute = (get & pathPrefix("debug")) {
    encodeResponse {
      getFromBrowseableDirectory(".")
    }
  }
}
