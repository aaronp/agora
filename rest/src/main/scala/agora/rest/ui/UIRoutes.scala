package agora.rest.ui

import agora.rest.ServerConfig
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{
  encodeResponse,
  extractUnmatchedPath,
  get,
  getFromBrowseableDirectory,
  getFromResource,
  mapUnmatchedPath,
  pathPrefix,
  redirect,
  _
}
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.language.reflectiveCalls

case class UIRoutes(docRoot: String = ".", defaultPath: String = "index.html") extends StrictLogging {

  import UIRoutes._

  def routes = jsRoute ~ uiRoute ~ rootRoute

  logger.debug(s"Serving UI routes under $docRoot")

  // ui/target/scala-2.11/classes
  private def resolveJsPath(uri: Uri.Path): Uri.Path = {
    uri.toString match {
      case Unslash(JavaScript(js)) =>
        val newPath: Uri.Path = Uri.Path("ui/target/scala-2.12/" + js)
        logger.debug(s"modified $uri to $newPath")
        newPath
      case _ => uri
    }
  }

  val uiRoute = (get & pathPrefix("ui")) {
    getFromBrowseableDirectory(docRoot)
  }

  val jsRoute = (get & pathPrefix("ui")) {
    mapUnmatchedPath(resolveJsPath) {
      encodeResponse {
        getFromBrowseableDirectory(docRoot)
      }
    }
  }

  /**
    * calls to <host>:<port>/
    * will redirect to the UI welcome page
    */
  val rootRoute = (get & pathEndOrSingleSlash) {
    logger.debug(s"Redirecting to $defaultPath")
    redirect(Uri(defaultPath), StatusCodes.TemporaryRedirect)
  }

}

object UIRoutes {

  private val SlashPrefixR = "/(.*)".r
  private val JsR          = "js/(.*)".r

  def unapply(serverConfig: ServerConfig) = {
    if (serverConfig.includeUIRoutes) {
      Option(UIRoutes(serverConfig.staticPath, serverConfig.defaultUIPath))
    } else {
      None
    }
  }

  private object Unslash {
    def unapply(input: String): Option[String] = input match {
      case SlashPrefixR(str) => Unslash.unapply(str)
      case other =>
        Option(other)
    }
  }

  private object JavaScript {
    def unapply(str: String): Option[String] = str match {
      case Unslash(JsR(str)) => Option(str)
      case _                 => None
    }
  }

}
