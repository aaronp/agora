package agora.rest.ui

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{encodeResponse, extractUnmatchedPath, get, getFromBrowseableDirectory, getFromResource, mapUnmatchedPath, pathPrefix, redirect, _}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._

import scala.language.reflectiveCalls

case class UIRoutes(defaultPath: String) {

  import UIRoutes._

  def routes = jsRoute ~ uiRoute ~ rootRoute

  // ui/target/scala-2.11/classes
  private def resolveJsPath(uri: Uri.Path): Uri.Path = {
    uri.toString match {
      case Unslash(JavaScript(js)) => Uri.Path("ui/target/scala-2.11/" + js)
      case _                       => uri
    }
  }

  val uiRoute = (get & pathPrefix("ui")) {
    extractUnmatchedPath { (unmatchedPath: Uri.Path) =>
      {
        val Unslash(r) = unmatchedPath.toString
        getFromResource(r)
      }
    }
  }
  val jsRoute = (get & pathPrefix("ui")) {
    mapUnmatchedPath(resolveJsPath) {
      encodeResponse {
        getFromBrowseableDirectory(".")
      }
    }
  }

  /**
    * calls to <host>:<port>/
    * will redirect to the UI welcome page
    */
  val rootRoute = (get & pathEndOrSingleSlash) {
    redirect(Uri(defaultPath), StatusCodes.TemporaryRedirect)
  }

  object debug {

    def routes = browseRoute

    val browseRoute = (get & pathPrefix("browse")) {
      encodeResponse {
        getFromBrowseableDirectory(".")
      }
    }
  }

}

object UIRoutes {

  private val SlashPrefixR = "/(.*)".r
  private val JsR          = "js/(.*)".r

  private object Unslash {
    def unapply(input: String): Option[String] = input match {
      case SlashPrefixR(str) =>
        val x = Unslash.unapply(str)
        x
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
