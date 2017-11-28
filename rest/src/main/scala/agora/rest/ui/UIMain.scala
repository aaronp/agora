package agora.rest.ui

import agora.rest.{RunningService, ServerConfig}
import agora.config._
import akka.http.scaladsl.model.Uri

object UIMain {
  def main(a: Array[String]): Unit = {
    val config           = new ServerConfig(configForArgs(a :+ "ui-example.conf").resolve()) //.getConfig("agora.worker")
    val path             = config.config.getString("staticPath")
    val routes: UIRoutes = UIRoutes(path)
    val service          = RunningService.start(config, routes.routes, config)
    println(s"Service $path on ${config.location.asHostPort}...")
  }
}
