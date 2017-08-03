package agora.exec.rest

import agora.api.Implicits._
import agora.api.`match`.MatchDetails
import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.json.JPath
import agora.api.worker.SubscriptionKey
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.workspace.WorkspaceId
import agora.rest.worker.{DynamicWorkerRoutes, RouteSubscriptionSupport}
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future

object ExecutionRoutes {

  /**
    * Creates a [[WorkSubscription]] for the given key (if provided)
    * It will match requests made with a 'command' key, asking for them to be
    * sent to /rest/exec/run
    *
    * @return
    */
  def execSubscription() = {
    // format: off
    WorkSubscription().
      withPath("/rest/exec/run").
      matchingSubmission(JPath("command").asMatcher)
    // format: on
  }

  def uploadSubscription() = {
    // format: off
    WorkSubscription().
      withPath("/rest/exec/upload").
      append("topic", "upload")
    // format: on
  }

  /**
    * This subscription depends on the 'uploadSubscription'
    * @param workspace
    * @param uploadSubscriptionKey
    */
  def uploadSubscriptionForWorkspace(workspace: WorkspaceId, uploadSubscriptionKey: SubscriptionKey) = {
    // format: off
    WorkSubscription().
      withPath("/rest/exec/upload").
      append("topic", "upload").
      append("workspace", workspace).
      referencing(uploadSubscriptionKey).
      matchingSubmission("workspace" === workspace)
    // format: on
  }

  def subscribeToExchange(exchange: Exchange,
                          execSubscriptionKey: SubscriptionKey,
                          initialExecSubscription: Int,
                          uploadSubscriptionKey: SubscriptionKey,
                          initialUploadSubscription: Int): Future[Nothing] = {
    for {
      execFuture   <- exchange.take(execSubscriptionKey, initialExecSubscription)
      uploadFuture <- exchange.take(uploadSubscriptionKey, initialUploadSubscription)
      _            <- execFuture
      _            <- uploadFuture
    } yield {
      true
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
    exchange: Exchange
) extends RouteSubscriptionSupport
    with FailFastCirceSupport {

  def routes(workerRoutes: DynamicWorkerRoutes, exchangeRoutes: Option[Route]): Route = {
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
        extractMatchDetails { (detailsOpt: Option[MatchDetails]) =>
          entity(as[Multipart.FormData]) { formData: Multipart.FormData =>
            parameter('workspace) { workspace =>
              detailsOpt.map(_.subscriptionKey).foreach { uploadSubscription =>
                }
              val uploadFuture = uploadToWorkspace(workspace, formData)
              complete(uploadFuture)
            }
          }
        }
      }
    }
  }

  def createUploadSubscription(workspace: WorkspaceId, uploadSubscription: SubscriptionKey) = {
    // format: off

      // format: on

    exchange.subscribe(workspaceSubscription).onComplete { resp =>
      logger.debug(s"Added $workspaceSubscription : $resp")
    }
  }
  def uploadToWorkspace(workspace: WorkspaceId, formData: Multipart.FormData) = {
    val saveTo                              = execConfig.uploadsDir.resolve(workspace)
    val uploadsFuture: Future[List[Upload]] = MultipartExtractor(formData, saveTo, execConfig.chunkSize)

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
