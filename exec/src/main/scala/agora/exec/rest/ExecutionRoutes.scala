package agora.exec.rest

import _root_.io.circe.generic.auto._
import _root_.io.swagger.annotations._
import agora.api.exchange.Exchange
import agora.exec.ExecConfig
import agora.exec.model._
import agora.exec.workspace.UploadDependencies
import agora.rest.worker.RouteSubscriptionSupport
import agora.time.TimeCoords
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.duration._

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
  * @param execConfig     the config from which worker routes will be built
  * @param exchange       the echange from which values will be requested should a request arrive with MatchDetails headers
  * @param executeHandler the request handler for running and cancelling jobs
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

    executeRoute ~ executeRouteGet ~ cancelRoute ~ execConfig.routes(exchangeRoutes) ~ workerRoutes.routes
  }

  @javax.ws.rs.Path("/rest/exec/run")
  @ApiOperation(value = "Execute a job and optionally stream the output",
                httpMethod = "POST",
                produces = "text/plain; charset=UTF-8",
                consumes = "application/json")
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
          import ctxt.executionContext
          takeNextOnComplete(exchange) {
            complete {
              logger.info(s"${execConfig.location.asHostPort} (POST) Handling ${inputProcess.commandString}")
              executeHandler.onExecutionRequest(ctxt.request, inputProcess)
            }
          }
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/exec/cancel")
  @ApiOperation(value = "Cancel a running job", httpMethod = "DELETE", produces = "text/plain; charset=UTF-8", consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "jobId", required = true, paramType = "query"),
      new ApiImplicitParam(name = "waitFor", required = false, paramType = "query", example = "100ms")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "a json boolean value indicating whether the cancel call cancelled the job"),
      new ApiResponse(code = 404, message = "If the job was not known/found")
    ))
  def cancelRoute = {
    (delete & path("rest" / "exec" / "cancel")) {
      parameter('jobId) { jobId =>
        parameter('waitFor.?) { waitForOpt =>
          val waitFor = waitForOpt match {
            case Some(TimeCoords.AsDuration(d)) => d
            case Some(other) =>
              sys.error(s"Invalid 'waitFor' value '${other}'. Please be sure to specify units (e.g. 100ms or 2 minutes)")
            case None => 0.millis
          }
          extractExecutionContext { implicit ec =>
            complete {
              executeHandler.onCancelJob(jobId, waitFor)
            }
          }
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/exec/run")
  @ApiOperation(value = "Execute a job and optionally stream the output",
                httpMethod = "GET",
                produces = "text/plain; charset=UTF-8",
                consumes = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "command", required = true, paramType = "query"),
      new ApiImplicitParam(name = "workspace", required = false, paramType = "query"),
      new ApiImplicitParam(name = "writeTo", required = false, paramType = "query"),
      new ApiImplicitParam(name = "env", required = false, paramType = "query"),
      new ApiImplicitParam(name = "canCache", required = false, paramType = "query", defaultValue = "false"),
      new ApiImplicitParam(name = "useCache", required = false, paramType = "query", defaultValue = "true")
    ))
  @ApiResponses(Array(new ApiResponse(code = 200, message = "the output of the command is returned w/ UTF-8 text encoding")))
  def executeRouteGet = {
    (get & path("rest" / "exec" / "run")) {
      (parameter('command) & parameter('workspace.?) & parameter('writeTo.?) & parameter('env.?) & parameter('canCache.?) & parameter('useCache.?)) {
        case (commandString, workspaceOpt, writeToOpt, envOpt, canCacheOpt, useCacheOpt) =>
          extractRequestContext { ctxt =>
            import ctxt.executionContext
            takeNextOnComplete(exchange) {
              complete {
                val inputProcess = {

                  val base          = RunProcess(commandString.split(" ", -1).toList)
                  val withWorkspace = workspaceOpt.map(_.trim).filterNot(_.isEmpty).fold(base)(base.withWorkspace)
                  val withStdOut =
                    writeToOpt.map(_.trim).filterNot(_.isEmpty).fold(withWorkspace)(withWorkspace.withStdOutTo)

                  val withEnv = envOpt.fold(withStdOut) { env =>
                    env.split(" ", -1).toList match {
                      case List(k, v) => withStdOut.withEnv(k, v)
                      case _          => withStdOut
                    }
                  }

                  val withCanCache = canCacheOpt.fold(withEnv) {
                    case "true" => withEnv.withCaching(true)
                    case _      => withEnv.withCaching(false)
                  }

                  val withUseCache = useCacheOpt.fold(withCanCache) {
                    case "true" => withCanCache.useCachedValueWhenAvailable(true)
                    case _      => withCanCache.useCachedValueWhenAvailable(false)
                  }

                  withUseCache
                }

                logger.info(s"${execConfig.location.asHostPort} (GET) Handling ${inputProcess.commandString}")
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
