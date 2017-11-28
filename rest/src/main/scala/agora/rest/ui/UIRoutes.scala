package agora.rest.ui

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
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.language.reflectiveCalls

case class UIRoutes(docRoot: String = ".", defaultPath: String = "index.html") {

  import UIRoutes._

  def routes = jsRoute ~ uiRoute ~ rootRoute

  // ui/target/scala-2.11/classes
  private def resolveJsPath(uri: Uri.Path): Uri.Path = {
    uri.toString match {
      case Unslash(JavaScript(js)) =>
        val newPath: Uri.Path = Uri.Path("ui/target/scala-2.12/" + js)
//        val resolved          = docRoot ++ newPath
//        println(s"$uri => $newPath => $resolved")
        newPath
      case _ => uri
    }
  }

  val uiRoute = (get & pathPrefix("ui")) {
    extractUnmatchedPath { (unmatchedPath: Uri.Path) =>
      {
        val Unslash(r) = unmatchedPath.toString
        val resolved   = docRoot + r
        println(s"Loading $r => $resolved")

        getFromResource(resolved.toString())
      }
    }
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
    println(s"Redirecting to $defaultPath  ")
    redirect(Uri(defaultPath), StatusCodes.TemporaryRedirect)
  }

  object debug {

    def routes = browseRoute

    val browseRoute = (get & pathPrefix("browse")) {
      encodeResponse {
        println(s"Browsing to $defaultPath  ")
        getFromBrowseableDirectory(docRoot)
      }
    }
  }

}

object UIRoutes {

  private val SlashPrefixR = "/(.*)".r
  private val JsR          = "js/(.*)".r

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
