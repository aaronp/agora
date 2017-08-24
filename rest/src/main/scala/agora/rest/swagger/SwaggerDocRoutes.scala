package agora.rest.swagger

import agora.rest.exchange.ExchangeRoutes
import agora.rest.support.SupportRoutes
import agora.rest.worker.DynamicWorkerRoutes
import com.github.swagger.akka.SwaggerHttpService
import com.github.swagger.akka.model.Info
import io.swagger.models.auth.BasicAuthDefinition

case class SwaggerDocRoutes(override val host: String) extends SwaggerHttpService {
  override val apiClasses: Set[Class[_]] = Set[Class[_]](
    classOf[SupportRoutes],
    classOf[DynamicWorkerRoutes],
    classOf[ExchangeRoutes]
  )
//  override val host                      = "localhost:12345"
  override val info = Info(version = "1.0")
//  override val externalDocs              = Some(new ExternalDocs("Core Docs", "http://acme.com/docs"))
  override val securitySchemeDefinitions = Map("basicAuth" -> new BasicAuthDefinition())
  override val unwantedDefinitions       = Seq("Function1", "Function1RequestContextFutureRouteResult")

  val site =
    path("swagger") { getFromResource("swagger-ui/index.html") } ~ getFromResourceDirectory("swagger-ui")

//  def logger = LoggerFactory.getLogger(getClass)

//  override def generateSwaggerJson: String = {
//    try {
//      Json.pretty().writeValueAsString(myNonShitFilteredSwagger)
//    } catch {
//      case NonFatal(t) => {
//        logger.error("Error creating swagger.json", t)
//        throw t
//      }
//    }
//  }
//
//  override def generateSwaggerYaml: String = {
//    try {
//      Yaml.pretty().writeValueAsString(myNonShitFilteredSwagger)
//    } catch {
//      case NonFatal(t) => {
//        logger.error("Error creating swagger.yaml", t)
//        throw t
//      }
//    }
//  }
//
//  private def myNonShitFilteredSwagger: Swagger = {
//    import scala.collection.JavaConverters._
//    val swagger: Swagger = reader.read(apiClasses.asJava)
//
//    swagger.setDefinitions(swagger.getDefinitions.asScala.filterKeys(definitionName => !unwantedDefinitions.contains(definitionName)).asJava)
//    swagger
//  }
}
