package agora.exec.rest

import agora.api.exchange.{WorkSubscription, WorkSubscriptionAck}
import agora.api.json.JPath
import agora.api.worker.SubscriptionKey
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.rest.exchange.ExchangeClient
import agora.rest.worker.{RouteSubscriptionSupport, WorkerRoutes}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ExecutionRoutes {
  def apply(execConfig: ExecConfig) = {


    val execSubscription = WorkSubscription().matchingSubmission(JPath("command").asMatcher)

    val exchange = execConfig.exchangeClient
      import execConfig.serverImplicits.executionContext

      // TODO - think of a better approach, like providing the subscription to this constructor
      exchange.subscribe(execSubscription).map { result =>
        new ExecutionRoutes(execConfig, exchange, result.id)
      }

  }
}

/**
  * Combines both the worker routes and some job output ones.
  *
  * NOTE: These routes are separate from the WorkerRoutes which handle
  * jobs that have been redirected from the exchange
  *
  * @param execConfig
  */
class ExecutionRoutes(val execConfig: ExecConfig, exchange: ExchangeClient, execSubscriptionKey : SubscriptionKey) extends RouteSubscriptionSupport with FailFastCirceSupport {

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(workerRoutes, exchangeRoutes) ~ execute ~ upload
  }

  def execute =
    post {
      (path("rest" / "exec" / "run") & pathEnd) {
        entity(as[RunProcess]) { runProcess =>
          extractRequestContext { ctxt =>
            import ctxt.executionContext
            extractMatchDetails {
              case detailsOpt@Some(details) =>

                /**
                  * We were called as part of a match on the exchange, so take another work item when this one finishes
                  */
                requestOnComplete(details, exchange) {
                  val runner = execConfig.newRunner(runProcess, detailsOpt, details.jobId)
                  val respFuture = ExecutionHandler(ctxt.request, runner, runProcess, detailsOpt)
                  complete(respFuture)
                }
              case None =>
                val runner = execConfig.newRunner(runProcess, None, agora.api.nextJobId())
                val respFuture = ExecutionHandler(ctxt.request, runner, runProcess, None)
                complete(respFuture)
            }
          }
        }
      }
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
      (path("rest" / "exec" / "upload") & pathEnd) {
        extractRequestContext { ctxt =>
          import ctxt.{executionContext, materializer}

          entity(as[Multipart.FormData]) { formData =>
            parameter('filename, 'workspace) { (filename, workspace) =>
              val saveTo = execConfig.uploadsDir.resolve(workspace)
              val uploadsFuture: Future[List[Upload]] = MultipartExtractor(formData, saveTo, execConfig.chunkSize)

              /**
                * Once the files are uploaded, create a subscription which contains 'file: [...], workspace: ...'
                * So that pending submits will find us (and not some other server's exec).
                */
              val uploadFuture: Future[WorkSubscriptionAck] = uploadsFuture.flatMap { uploads =>
                val fileNames = uploads.map(_.name).toSet
                val upserted = ExecutionHandler.newWorkspaceSubscription(execSubscriptionKey, workspace, fileNames)
                exchange.subscribe(upserted).map { ack =>
                  import io.circe.syntax._
                  import io.circe.generic.auto._
                  fileNames.asJ

                }
              }

              complete {
                uploadFuture
              }
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
