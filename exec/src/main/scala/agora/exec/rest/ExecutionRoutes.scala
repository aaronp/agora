package agora.exec.rest

import agora.api.Implicits._
import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.json.JPath
import agora.api.worker.SubscriptionKey
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.workspace.WorkspaceId
import agora.rest.worker.{RouteSubscriptionSupport, WorkerRoutes}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future

object ExecutionRoutes {

  /** @param execConfig a configuration
    * @return a future of the ExecutionRoutes once the exec subscription completes
    */
  def apply(execConfig: ExecConfig): Future[ExecutionRoutes] = apply(execConfig, execConfig.exchangeClient)

  /** @param execConfig a configuration
    * @param exchange   the exchange to subscribe to
    * @return a future of the ExecutionRoutes once the exec subscription completes
    */
  def apply(execConfig: ExecConfig, exchange: Exchange): Future[ExecutionRoutes] = {
    val execSubscription   = WorkSubscription().matchingSubmission(JPath("command").asMatcher)
    val uploadSubscription = WorkSubscription().append("topic", "upload")

    val execAckFut   = exchange.subscribe(execSubscription)
    val uploadAckFut = exchange.subscribe(uploadSubscription)

    // subscribe to handle 'execute' requests
    for {
      execAck   <- execAckFut
      uploadAck <- uploadAckFut
    } yield {
      new ExecutionRoutes(execConfig, exchange, execAck.id, uploadAck.id)
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
case class ExecutionRoutes(
    execConfig: ExecConfig,
    exchange: Exchange,
    execSubscriptionKey: SubscriptionKey,
    uploadSubscriptionKey: SubscriptionKey
) extends RouteSubscriptionSupport
    with FailFastCirceSupport {

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(workerRoutes, exchangeRoutes) ~ execRoutes
  }

  def execRoutes = executeRoute ~ uploadRoute

  def executeRoute = {
    post {
      (path("rest" / "exec" / "run") & pathEnd) {
        entity(as[RunProcess]) { runProcess =>
          extractRequestContext { ctxt =>
            import ctxt.executionContext
            extractMatchDetails {
              case detailsOpt @ Some(details) =>
                /**
                  * We were called as part of a match on the exchange, so take another work item when this one finishes
                  */
                requestOnComplete(details, exchange) {
                  val runner     = execConfig.newRunner(runProcess, detailsOpt, details.jobId)
                  val respFuture = ExecutionHandler(ctxt.request, runner, runProcess, detailsOpt)
                  complete(respFuture)
                }
              case None =>
                val runner     = execConfig.newRunner(runProcess, None, agora.api.nextJobId())
                val respFuture = ExecutionHandler(ctxt.request, runner, runProcess, None)
                complete(respFuture)
            }
          }
        }
      }
    }
  }

  /**
    * Uploads some files to a workspace.
    *
    * We start with creating a subscription for 'topic : upload'.
    * When summat gets uploaded we add a subscription for 'workspace : xyz' based
    * on the upload subscription
    *
    * When a file is uploaded, a new subscription is added for the new files/workspace
    *
    * @return a Route used to update
    */
  def uploadRoute: Route = {
    (post & path("rest" / "exec" / "upload") & pathEnd) {
      extractRequestContext { ctxt =>
        import ctxt.{executionContext, materializer}

        entity(as[Multipart.FormData]) { formData: Multipart.FormData =>
          parameter('workspace) { workspace =>
            val uploadFuture = uploadToWorkspace(workspace, formData)
            complete(uploadFuture)
          }
        }
      }
    }
  }

  def uploadToWorkspace(workspace: WorkspaceId, formData: Multipart.FormData) = {
    val saveTo                              = execConfig.uploadsDir.resolve(workspace)
    val uploadsFuture: Future[List[Upload]] = MultipartExtractor(formData, saveTo, execConfig.chunkSize)

    // subscribe to this workspace. We can do this async
    {
      // format: off
      val workspaceSubscription = WorkSubscription().
        append("topic", "upload").
        append("workspace", workspace).
        referencing(uploadSubscriptionKey).
        matchingSubmission(("workspace" === workspace).asMatcher)
      // format: on

      exchange.subscribe(workspaceSubscription).onComplete { resp =>
        logger.debug(s"Added $workspaceSubscription : $resp")
      }
    }

    /**
      * Once the files are uploaded, create a subscription which contains 'file: [...], workspace: ...'
      * So that pending submits will find us (and not some other server's exec).
      */
    uploadsFuture.flatMap { uploads =>
      val fileNames = uploads.map(_.name).toSet
      val upserted  = ExecutionHandler.newWorkspaceSubscription(execSubscriptionKey, workspace, fileNames)
      exchange.subscribe(upserted).map { ack =>
        import io.circe.syntax._
        fileNames.asJson
      }
    }
  }

  override def toString = s"ExecutionRoutes {${execConfig.describe}}"

}
