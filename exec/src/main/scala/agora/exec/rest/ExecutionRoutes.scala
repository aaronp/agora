package agora.exec.rest

import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.io.implicits._
import agora.rest.worker.{RouteSubscriptionSupport, WorkerRoutes}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._

object ExecutionRoutes {
  def apply(execConfig: ExecConfig) = new ExecutionRoutes(execConfig)
}

/**
  * Combines both the worker routes and some job output ones.
  *
  * NOTE: These routes are separate from the WorkerRoutes which handle
  * jobs that have been redirected from the exchange
  *
  * @param execConfig
  */
class ExecutionRoutes(val execConfig: ExecConfig) extends RouteSubscriptionSupport with StrictLogging with FailFastCirceSupport {

  import execConfig._

  lazy val exchange = execConfig.exchangeClient

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(workerRoutes, exchangeRoutes) ~ jobRoutes
  }

  def execute = {
    post {
      entity(as[RunProcess]) { runProcess =>
        takeNextOnComplete(exchange) {
          ???
        }
      }
    }
  }

  def jobRoutes = pathPrefix("rest" / "exec") {
    listJobs ~ execute
  }

  def listJobs: Route = {
    get {
      (path("jobs") & pathEnd) {
        complete {
          val ids = logs.path.fold(List[Json]()) { dir =>
            dir.children.toList.sortBy(_.created.toEpochMilli).map { child =>
              Json.obj("id" -> Json.fromString(child.fileName), "started" -> Json.fromString(child.createdString))
            }
          }
          Json.arr(ids: _*)
        }
      }
    }
  }

  override def toString = {
    s"ExecutionRoutes {${execConfig.describe}}"
  }

}
