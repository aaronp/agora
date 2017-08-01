package agora.exec.rest

import agora.api.exchange.{QueueState, WorkSubscription}
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.rest.worker.{RouteSubscriptionSupport, WorkerRoutes}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future

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
class ExecutionRoutes(val execConfig: ExecConfig) extends
  RouteSubscriptionSupport
  with FailFastCirceSupport {

  lazy val exchange = execConfig.exchangeClient

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(workerRoutes, exchangeRoutes) ~ jobRoutes
  }

  def jobRoutes = pathPrefix("rest" / "exec") {
    execute
  }

  /**
    * Uploads some files to a workspace.
    *
    * When a file is uploaded, a new subscription is added for the new files/workspace
    *
    * @return
    */
  def upload = {
    post {
      (path("upload") & pathEnd) {
        entity(as[Multipart.FormData]) { formData =>

          parameter('workspace) { workspace =>

            val uploadsFuture: Future[List[Upload]] = MultipartExtractor(formData, execConfig.uploadsDir, execConfig.chunkSize)
            uploadsFuture.map { uploads =>
              val sub = ExecutionHandler.newWorkspaceSubscription(workspace, uploads.map(_.name).toSet)
              exchange.queueState(QueueState())
              exchange.subscribe(sub)
            }

            complete {

            }
          }
        }
      }
    }
  }

  /**
    * Executes a RunProcess and pulls another work item upon completion.
    *
    * @return the route for executing a RunProcess request
    */
  def execute = {
    post {
      (path("run") & pathEnd) {
        entity(as[RunProcess]) { runProcess =>
          extractRequest { httpReq =>
            extractMatchDetails {
              case detailsOpt@Some(details) =>
                requestOnComplete(details, exchange) {
                  val runner = execConfig.newRunner(runProcess, detailsOpt, details.jobId)
                  val respFuture = ExecutionHandler(httpReq, runner, runProcess, detailsOpt)
                  complete(respFuture)
                }
              case detailsOpt =>
                val runner = execConfig.newRunner(runProcess, detailsOpt, agora.api.nextJobId())
                val respFuture = ExecutionHandler(httpReq, runner, runProcess, detailsOpt)
                complete(respFuture)
            }
          }
        }
      }
    }
  }

  override def toString = {
    s"ExecutionRoutes {${execConfig.describe}}"
  }

}
