package agora.rest.support

import javax.ws.rs.Path

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.Config
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
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return the server configuration", response = classOf[SwaggerJson])
    ))
  def getConfig = (get & pathPrefix("debug" / "config")) {
    complete {
      import agora.domain.RichConfig.implicits._
      HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, config.json))
    }
  }

  //  @ApiOperation(value = "Return a resource at the given path", nickname = "debugPath", httpMethod = "GET", response = classOf[String])
  //  @ApiResponses(
  //    Array(
  //      new ApiResponse(code = 200, message = "Return the server configuration", response = classOf[String]),
  //      new ApiResponse(code = 404, message = "Not Found"),
  //      new ApiResponse(code = 500, message = "Internal server error")
  //    ))
  //  @Path("/rest/debug/{path}")
  //  @ApiImplicitParams(
  //    Array(
  //      new ApiImplicitParam(name = "path", value = "The resource to return", required = true, dataType = "string", paramType = "path")
  //    ))
  //  val debugRoute = (get & pathPrefix("debug")) {
  //    encodeResponse {
  //      getFromBrowseableDirectory(".")
  //    }
  //  }
}
