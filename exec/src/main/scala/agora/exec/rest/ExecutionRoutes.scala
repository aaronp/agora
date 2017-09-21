package agora.exec.rest

import _root_.io.circe.generic.auto._
import _root_.io.swagger.annotations._
import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.model._
import agora.exec.workspace.UploadDependencies
import agora.rest.worker.RouteSubscriptionSupport
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

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
  * Combines both the worker routes and some job output ones.
  *
  * NOTE: These routes are separate from the WorkerRoutes which handle
  * jobs that have been redirected from the exchange
  *
  * @param execConfig
  */
@Api(value = "Execute", produces = "application/json")
@javax.ws.rs.Path("/")
case class ExecutionRoutes(
    execConfig: ExecConfig,
    exchange: Exchange,
    executeHandler: ExecutionWorkflow
) extends RouteSubscriptionSupport
    with FailFastCirceSupport {

  def routes(exchangeRoutes: Option[Route]): Route = {
    val workerRoutes = execConfig.newWorkerRoutes(exchange)

    executeRoute ~ execConfig.routes(exchangeRoutes) ~ workerRoutes.routes
  }

  @javax.ws.rs.Path("/rest/exec/run")
  @ApiOperation(value = "Execute a job and optionally stream the output", httpMethod = "POST", produces = "text/plain; charset=UTF-8", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body", required = true, dataTypeClass = classOf[RunProcess], paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "the output of the command is returned w/ UTF-8 text encoding"),
      new ApiResponse(code = 200, message = "The file output summary when output streaming is not requested", response = classOf[FileResult])
    ))
  def executeRoute = {
    (post & path("rest" / "exec" / "run")) {
      entity(as[RunProcess]) { inputProcess =>
        extractRequestContext { ctxt =>
          takeNextOnComplete(exchange) {
            complete {
              import ctxt.executionContext
              executeHandler.onExecutionRequest(ctxt.request, inputProcess)
            }
          }
        }
      }
    }
  }

  override def toString = s"ExecutionRoutes {${execConfig.describe}}"
}

object ExecutionRoutes {
  def apply(execConfig: ExecConfig, exchange: Exchange = Exchange.instance()): ExecutionRoutes = {
    val workflow = ExecutionWorkflow(execConfig.defaultEnv, execConfig.workspaceClient, execConfig.eventMonitor, execConfig.enableCache)
    new ExecutionRoutes(execConfig, exchange, workflow)
  }
}
