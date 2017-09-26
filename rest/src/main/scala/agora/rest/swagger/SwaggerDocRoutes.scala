package agora.rest.swagger

import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition

case class SwaggerDocRoutes(override val host: String, override val apiClasses: Set[Class[_]])
    extends SwaggerHttpService {

  override val info                      = Info(version = "1.0")
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
  override val unwantedDefinitions       = Seq("Function1", "Function1RequestContextFutureRouteResult")

  val site =
    path("swagger") {
      getFromResource("swagger-ui/index.html")
    } ~ getFromResourceDirectory("swagger-ui")

}
