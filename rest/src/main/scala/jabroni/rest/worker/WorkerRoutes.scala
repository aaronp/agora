package jabroni.rest.worker

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.auto._
import jabroni.api.worker.DispatchWork

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class WorkerRoutes(handlersByName: Map[String, Handler])(implicit ec: ExecutionContext) {

  // http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html

  def routes: Route = pathPrefix("rest" / "worker") {
    val handlerRoutes = handlersByName.map {
      case (name, h) => routeForHandler(name, h)
    }
    handlerRoutes.reduce(_ ~ _)
  }


  def routeForHandler(name: String, h: Handler) = (put & path(name) & pathEnd) {
    entity(as[DispatchWork]) { workItem =>
      complete {
        h.onWork(workItem)
      }
    }
  }
}