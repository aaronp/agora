package agora.rest.support

import javax.ws.rs.Path

import agora.config.implicits._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
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
    getConfig ~ setLogLevel ~ setLogging ~ getLogging
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

  @Path("/rest/debug/loglevel")
  @ApiOperation(value = "Sets a new log level for the given name", httpMethod = "GET")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "name",
                           example = "agora.rest",
                           defaultValue = "",
                           value = "the logger name (or package)",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "level",
                           example = "info",
                           defaultValue = "",
                           value = "the new name log level to set",
                           required = true,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "a json response containing the before and after log level")
    ))
  def setLogLevel = (get & pathPrefix("debug" / "loglevel")) {
    parameter('name, 'level) { (name, level) =>
      complete {

        LoggingOps.setLevel(name, level) match {
          case Some((oldLevel, newLevel)) =>
            Map("old" -> oldLevel, "new" -> newLevel).asJson.noSpaces
          case None =>
            "Cannot set log levels"
        }
      }
    }
  }

  @Path("/rest/debug/logging")
  @ApiOperation(value = "Reconfigure the logging", nickname = "logging", httpMethod = "POST")
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return an ok status result when successful")
    ))
  def setLogging = (post & pathPrefix("debug" / "logging")) {
    entity(as[String]) { configText =>
      complete {
        val ok = LoggingOps.reset(configText)
        if (ok) {
          HttpResponse(StatusCodes.OK)
        } else {
          HttpResponse(StatusCodes.InternalServerError)
        }
      }
    }
  }

  @Path("/rest/debug/logging")
  @ApiOperation(value = "Get the current logging configuration", httpMethod = "GET")
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "Return an ok status result when successful")
    ))
  def getLogging = (get & pathPrefix("debug" / "logging")) {
    complete {
      val text = LoggingOps.config().getOrElse("logback.xml not found on the classpath")
      HttpResponse().withEntity(HttpEntity(text))
    }
  }
}
