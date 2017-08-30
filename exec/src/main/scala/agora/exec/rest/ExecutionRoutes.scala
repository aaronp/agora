package agora.exec.rest

import java.nio.file.Path

import agora.api.Implicits._
import agora.api._
import agora.api.exchange.{Exchange, WorkSubscription, WorkSubscriptionAck}
import agora.api.json.JMatcher
import agora.exec.ExecConfig
import agora.exec.model._
import agora.exec.workspace.{UploadDependencies, WorkspaceClient}
import agora.rest.MatchDetailsExtractor
import agora.rest.worker.RouteSubscriptionSupport
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import io.swagger.annotations._

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
  val execCriteria: JMatcher        = ("topic" === "execute").asMatcher
  val execAndSaveCriteria: JMatcher = ("topic" === "executeAndSave").asMatcher

}

/**
  * Combines both the worker routes and some job output ones.
  *
  * NOTE: These routes are separate from the WorkerRoutes which handle
  * jobs that have been redirected from the exchange
  *
  * @param execConfig
  */

@Api(value = "Execute", produces = "application/json")
@javax.ws.rs.Path("/")
case class ExecutionRoutes(execConfig: ExecConfig, exchange: Exchange, workspaces: WorkspaceClient) extends RouteSubscriptionSupport with FailFastCirceSupport {

  def routes(exchangeRoutes: Option[Route]): Route = {
    val workerRoutes = execConfig.newWorkerRoutes(exchange)

    executeRoute ~ executeAndSaveRoute ~ execConfig.routes(exchangeRoutes) ~ workerRoutes.routes
  }

  private def execute(runProcess: StreamingProcess, httpRequest: HttpRequest, workingDir: Option[Path])(implicit ec: ExecutionContext): Future[HttpResponse] = {
    val detailsOpt = MatchDetailsExtractor.unapply(httpRequest)
    val jobId      = detailsOpt.map(_.jobId).getOrElse(nextJobId)

    val runner = execConfig.newRunner(runProcess, detailsOpt, workingDir, jobId)
    ExecutionHandler(httpRequest, runner, runProcess, detailsOpt)

  }

  @javax.ws.rs.Path("/rest/exec/run")
  @ApiOperation(value = "Execute a job and stream the output", httpMethod = "POST", produces = "text/plain(UTF-8)", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", required = true, dataTypeClass = classOf[StreamingProcess], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "the output of the command is returned w/ UTF-8 text encoding")
    ))
  def executeRoute = {
    (post & path("rest" / "exec" / "run")) {
      entity(as[StreamingProcess]) { runProcess =>
        logger.info(s"Running ${runProcess.commandString}")
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

  // TODO - consider making this part of the same route as 'run' and just unmarshal the body differently
  @javax.ws.rs.Path("/rest/exec/save")
  @ApiOperation(value = "Execute a job for its side-effects, saving the results to a specified workspace", httpMethod = "POST")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", required = true, dataTypeClass = classOf[ExecuteProcess], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "???", response = classOf[ResultSavingRunProcessResponse])
    ))
  def executeAndSaveRoute = {
    (post & path("rest" / "exec" / "save")) {
      entity(as[ExecuteProcess]) { runProcess =>
        logger.info(s"Running ${runProcess.asStreamingProcess.commandString}")
        extractRequestContext { ctxt =>
          import ctxt.executionContext

          takeNextOnComplete(exchange) {

            val matchDetails   = MatchDetailsExtractor.unapply(ctxt.request)
            val responseFuture = ExecutionHandler.executeAndSave(execConfig, workspaces, runProcess, matchDetails)

            val future = responseFuture.map { resp =>
              HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, resp.asJson.noSpaces))
            }
            complete(future)
          }
        }
      }
    }
  }

  override def toString = s"ExecutionRoutes {${execConfig.describe}}"
}
