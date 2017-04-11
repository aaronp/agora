package jabroni.rest.server

import scala.language.reflectiveCalls

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import jabroni.api.{Buy, Ledger, Order, Sell}
import jabroni.json.JsonSupport
import io.circe.Json
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext

case class FinanceRoutes(ledger: Ledger)(implicit ec: ExecutionContext) extends JsonSupport {

  // http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
  def routes: Route = rest.routes ~ ui.routes ~ debug.routes


  /** The REST endpoint routes
    */
  object rest {
    def routes: Route = pathPrefix("rest") {
      buyRoute ~ sellRoute ~ ordersRoute ~ cancelRoute
    }

    private def asResponse(json: Json) = HttpEntity(`application/json`, json.noSpaces)

    val buyRoute = (put & path("buy") & pathEnd) {
      entity(as[Order]) { order =>
        complete {
          require(order.`type` == Buy, s"Expected 'buy', but got '${order.`type`}'")
          ledger.placeOrder(order).map(Json.fromBoolean).map(asResponse)
        }
      }
    }
    val cancelRoute = (delete & path("cancel") & pathEnd) {
      entity(as[Order]) { order =>
        complete {
          ledger.cancelOrder(order).map(Json.fromBoolean).map(asResponse)
        }
      }
    }
    val sellRoute = (put & path("sell") & pathEnd) {
      entity(as[Order]) { order =>
        complete {
          require(order.`type` == Sell, s"Expected 'sell', but got '${order.`type`}'")
          ledger.placeOrder(order).map(Json.fromBoolean).map(asResponse)
        }
      }
    }
    val ordersRoute = (get & path("orders") & pathEnd) {
      complete {
        ledger.orderBook.map(_.toJson).map(asResponse)
      }
    }

    val debugRoute = (get & pathPrefix("debug")) {
      encodeResponse {
        getFromBrowseableDirectory(".")
      }
    }
  }


  object debug {

    def routes = browseRoute

    val browseRoute = (get & pathPrefix("browse")) {
      encodeResponse {
        getFromBrowseableDirectory(".")
      }
    }
  }

  object ui {

    import FinanceRoutes._

    def routes = jsRoute ~ uiRoute ~ rootRoute

    private def resolveJsPath(uri: Uri.Path): Uri.Path = {
      uri.toString match {
        case Unslash(JavaScript(js)) => Uri.Path("ui/target/scala-2.11/" + js)
        case _ => uri
      }
    }

    val uiRoute = (get & pathPrefix("ui")) {
      extractUnmatchedPath { (unmatchedPath: Uri.Path) =>
        encodeResponse {
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
      redirect(Uri("ui/index.html"), StatusCodes.TemporaryRedirect)
    }
  }

}

object FinanceRoutes {

  private val SlashPrefixR = "/(.*)".r
  private val JsR = "js/(.*)".r

  private object Unslash {
    def unapply(str: String): Option[String] = str match {
      case SlashPrefixR(str) => Unslash.unapply(str)
      case other => Option(other)
    }
  }

  private object JavaScript {
    def unapply(str: String): Option[String] = str match {
      case Unslash(JsR(str)) => Option(str)
      case _ => None
    }
  }

}
