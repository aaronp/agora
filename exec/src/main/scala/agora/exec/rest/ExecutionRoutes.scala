package agora.exec.rest

import java.nio.file.Path

import agora.api.Implicits._
import agora.api._
import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.json.{JMatcher, JPath}
import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.workspace.{UploadDependencies, WorkspaceClient}
import agora.rest.MatchDetailsExtractor
import agora.rest.worker.{RouteSubscriptionSupport, WorkerConfig}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}

/**
  * The execution routes can execute commands on the machine, as well as upload files to workspaces.
  *
  * == Use Case Example w/ Spark ==
  * Consider an Apache Spark use-case, where we have a command we want to execute on a machine
  * which depends on data being uploaded from some worker nodes.
  *
  * One approach would be to execute an initial command in order to 'select' a number of workers.
  * That could be a command to actually get data, or just choose which workers match particular criteria.
  *
  * Once we have chosen that/those worker(s), we have each Spark node make a request to upload its partitioned data
  * to them in a prepare step asynchronously (fire and forget).
  *
  * The workers could then perform actions based on normal executions via the exchange which specify selection criteria
  * that target those specific nodes (as we want to ensure our commands operate on the same files which were uploaded
  * by specifying [[UploadDependencies]].
  *
  * == Subscriptions ==
  * 1) Execute -- subscribe with "topic=execute" (which also matches work requests with a 'command' json element).
  *
  */
object ExecutionRoutes {
  val execCriteria: JMatcher = ("topic" === "execute").asMatcher

  /** It will match requests made with a 'command' key, asking for them to be
    * sent to /rest/exec/run
    *
    * @return a [[WorkSubscription]] for the given key (if provided)
    */
  def execSubscription(config: WorkerConfig): WorkSubscription = {
    // format: off
    val sub = config.subscription.
      withPath("/rest/exec/run").
      append("topic", "execute").
      matchingJob(JPath("command").asMatcher)
    // format: on
    assert(execCriteria.matches(sub.details.aboutMe))
    sub
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
case class ExecutionRoutes(execConfig: ExecConfig, exchange: Exchange, workspaces: WorkspaceClient) extends RouteSubscriptionSupport with FailFastCirceSupport {

  def routes(exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(exchangeRoutes) ~ executeRoute
  }

  private def execute(runProcess: RunProcess, httpRequest: HttpRequest, workingDir: Option[Path])(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val detailsOpt = MatchDetailsExtractor.unapply(httpRequest)
    val jobId      = detailsOpt.map(_.jobId).getOrElse(nextJobId)
    val runner     = execConfig.newRunner(runProcess, detailsOpt, workingDir, jobId)
    ExecutionHandler(httpRequest, runner, runProcess, detailsOpt)
  }

  def executeRoute = {
    (post & path("rest" / "exec" / "run")) {
      entity(as[RunProcess]) { runProcess =>
        extractRequestContext { ctxt =>
          import ctxt.executionContext

          takeNextOnComplete(exchange) {
            val future = runProcess.dependencies match {
              case None                                                           => execute(runProcess, ctxt.request, None)
              case Some(UploadDependencies(workspace, fileDependencies, timeout)) =>
                // ensure we wait for all files to arrive
                workspaces.await(workspace, fileDependencies, timeout).flatMap { path =>
                  execute(runProcess, ctxt.request, Option(path))
                }
            }
            complete(future)
          }
        }
      }
    }
  }

  override def toString = s"ExecutionRoutes {${execConfig.describe}}"
}
