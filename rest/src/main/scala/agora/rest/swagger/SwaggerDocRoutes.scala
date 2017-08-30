package agora.rest.swagger

import agora.rest.exchange.ExchangeRoutes
import agora.rest.support.SupportRoutes
import agora.rest.worker.DynamicWorkerRoutes
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition

case class SwaggerDocRoutes(override val host: String, override val apiClasses: Set[Class[_]]) extends SwaggerHttpService {

  //  override val host                      = "localhost:12345"
  override val info = Info(version = "1.0")
  //  override val externalDocs              = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
  override val unwantedDefinitions       = Seq("Function1", "Function1RequestContextFutureRouteResult")

  val site =
    path("swagger") {
      getFromResource("swagger-ui/index.html")
    } ~ getFromResourceDirectory("swagger-ui")

}
