package agora.rest.stream

import agora.rest.ui.UIRoutes
import agora.rest.{RunningService, ServerConfig}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging

object Demo extends App with StrictLogging {
  val conf = ServerConfig(args)
  val sr   = new StreamRoutes

  val routes: Route = UIRoutes.unapply(conf).fold(sr.routes)(_.routes ~ sr.routes)

  logger.info(s"Starting demo on ${conf.location} w/ UI paths under ${conf.defaultUIPath}")
  RunningService.start(conf, routes, sr)

}
