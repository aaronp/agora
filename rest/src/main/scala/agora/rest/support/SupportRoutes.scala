package agora.rest.support

import javax.ws.rs.Path

import agora.config.RichConfig.implicits._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
import io.circe.generic.auto._
import io.circe.syntax._
import io.swagger.annotations._
import io.swagger.util.{Json => SwaggerJson}

import scala.language.reflectiveCalls

@Api(value = "Support", produces = "application/json")
@Path("/")
case class SupportRoutes(config: Config) {

  def routes: Route = pathPrefix("rest") {
    getConfig
  }

  @Path("/rest/debug/config")
  @ApiOperation(value = "Return the server configuration", nickname = "config", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "filter",
                           example = "host",
                           defaultValue = "",
                           value = "some text on which to filter configuration keys",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return the server configuration", response = classOf[SwaggerJson])
    ))
  def getConfig = (get & pathPrefix("debug" / "config")) {
    parameter('filter.?) {
      case None =>
        complete {
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, config.json))
        }
      case Some(filter) =>
        val matchingLines = config.summary(_.contains(filter))
        complete {
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, matchingLines.asJson.noSpaces))
        }
    }
  }
}
