package agora.exec.rest

import java.nio.file.Path

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.exec.client.LocalRunner
import agora.exec.events.{CompletedJob, ReceivedJob, StartedJob, SystemEventMonitor}
import agora.exec.log.{IterableLogger, ProcessLoggers}
import agora.exec.model.{FileResult, ProcessException, RunProcess, StreamingResult}
import agora.exec.workspace.WorkspaceClient
import agora.rest.MatchDetailsExtractor
import akka.NotUsed
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger
import scala.util.Failure
import scala.util.control.NonFatal

/**
  * Represents a handler which will be triggered from the given [[HttpRequest]] when a [[RunProcess]] is received
  *
  */
trait ExecutionWorkflow {

  /**
    * The job is received. Do something with it and reply...
    *
    * @param httpRequest  the originating http request
    * @param inputProcess the unmarshalled input process
    * @return the eventual [[HttpResponse]]
    */
  def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(
      implicit ec: ExecutionContext): Future[HttpResponse]
}

object ExecutionWorkflow extends StrictLogging with FailFastCirceSupport {

  /**
    * A workflow which will check .cache directories in the request's workspace
    *
    * @param workspaces
    * @param underlying
    */
  class CachingWorkflow(workspaces: WorkspaceClient, underlying: ExecutionWorkflow) extends ExecutionWorkflow {
    override def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(
        implicit ec: ExecutionContext): Future[HttpResponse] = {
      if (inputProcess.output.useCachedValueWhenAvailable) {
        checkCache(httpRequest, inputProcess)
      } else {
        processUncached(httpRequest, inputProcess)
      }
    }

    protected def processUncached(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext) = {
      underlying.onExecutionRequest(httpRequest, inputProcess)
    }

    // 'await' a no-op dependency to get the working directory
    private def workDirFuture(inputProcess: RunProcess): Future[Path] = {
      workspaces.await(inputProcess.dependencies.copy(dependsOnFiles = Set.empty, timeoutInMillis = 0))
    }

    def checkCache(httpRequest: HttpRequest, inputProcess: RunProcess)(
        implicit ec: ExecutionContext): Future[HttpResponse] = {

      import akka.http.scaladsl.util.FastFuture._
      workDirFuture(inputProcess).fast.flatMap { workingDir =>
        val cacheDir = CachedOutput.cacheDir(workingDir, inputProcess)
        CachedOutput.cachedResponse(cacheDir, httpRequest, inputProcess).getOrElse {
          processUncached(httpRequest, inputProcess)
        }
      }
    }
  }

  /**
    * Creates a workflow with which to handle incoming execution requests.
    *
    * @param defaultEnv   any system properties used to inject into incoming requests
    * @param workspaces   the workspaces system to use in working out job dependencies and working directories in which
    *                     to run the jobs
    * @param eventMonitor a monitor which we can alert w/ job notifications and other interesting events
    * @return an ExecutionWorkflow for handling jobs originating from [[HttpRequest]]s
    */
  def apply(defaultEnv: Map[String, String],
            workspaces: WorkspaceClient,
            eventMonitor: SystemEventMonitor,
            enableCacheCheck: Boolean = false): ExecutionWorkflow = {
    val instance = new Instance(defaultEnv, workspaces, eventMonitor, enableCacheCheck)
    if (enableCacheCheck) {
      new CachingWorkflow(workspaces, instance)
    } else {
      instance
    }
  }

  /**
    * Executes the 'RunProcess' using the given 'defaultEnv' (default system properties),
    * workspace, and input request.
    *
    * The workflow is:
    *
    * 1) await any dependencies declared on the [[RunProcess]] using the [[WorkspaceClient]]
    * 2) upon success of #1, a [[ProcessLoggers]] is created for the job in the working directory
    * using the [[MatchDetails]] extracted from the [[HttpRequest]] headers (if any). Any std out
    * or std err files from the [[RunProcess]] are appended to the [[ProcessLoggers]] used to run the job.
    * 3) An [[HttpResponse]] future is prepared based on the [[HttpRequest]] given. That response will either be a
    * [[FileResult]] if no streaming settings are provided or a [[StreamingResult]] if result streaming
    * was specified by the [[RunProcess.output.streaming]].
    *
    * Note: if any std out or std err files were specified, then the [[WorkspaceClient.triggerUploadCheck]]
    * will be invoked to re-check any files which may depend on the output
    *
    * @param workspaces   the workspaces to use in determining/awaiting working directories
    * @param eventMonitor a monitor to notify of system events
    * @return the HttpResponse in a future
    */
  class Instance(val defaultEnv: Map[String, String],
                 val workspaces: WorkspaceClient,
                 val eventMonitor: SystemEventMonitor,
                 cacheEnabled: Boolean)
      extends ExecutionWorkflow {

    override def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(
        implicit ec: ExecutionContext): Future[HttpResponse] = {

      /** 1) Add any system (configuration) wide environment properties to the input request */
      val runProcess: RunProcess = {
        val withEnv = inputProcess.withEnv(defaultEnv ++ inputProcess.env).resolveEnv

        if (cacheEnabled) {
          withEnv.ensuringCacheOutputs
        } else {
          withEnv
        }
      }

      /** 2) either obtain or stamp a unique id on this request */
      val detailsOpt: Option[MatchDetails] = MatchDetailsExtractor.unapply(httpRequest)
      val jobId                            = detailsOpt.map(_.jobId).getOrElse(agora.api.nextJobId())

      /** 3) let the monitor know we've accepted a job */
      eventMonitor.accept(ReceivedJob(jobId, detailsOpt, runProcess))

      /** 4) obtain a workspace in which to run the job
        * this may eventually time-out based on the workspaces configuration
        */
      workspaces.await(runProcess.dependencies).flatMap { (workingDir: Path) =>
        eventMonitor.accept(StartedJob(jobId))
        onJob(httpRequest, workingDir, jobId, detailsOpt, runProcess)
      }
    }

    protected def onJob(httpRequest: HttpRequest,
                        workingDir: Path,
                        jobId: JobId,
                        detailsOpt: Option[MatchDetails],
                        runProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse] = {
      val processLogger: ProcessLoggers = loggerForJob(runProcess, detailsOpt, workingDir)

      /** actually execute the [[RunProcess]] and return the Future[Int] of the exit code */
      val exitCodeFuture: Future[Int] = invokeJob(jobId, workingDir, runProcess, processLogger)

      /** If our job writes to a file, we should trigger a workspace check when that job exits */
      if (runProcess.hasFileOutputs) {
        exitCodeFuture.onComplete {
          case _ => workspaces.triggerUploadCheck(runProcess.workspace)
        }
      }

      /**
        * If we can cache (and we got this far ... hence implying we've not already been able to take advantage of a
        * cached value)
        */
      if (cacheEnabled && runProcess.output.canCache) {
        exitCodeFuture.onSuccess {
          case exitCode =>
            val cacheDir = CachedOutput.cacheDir(workingDir, runProcess)
            CachedOutput.cache(cacheDir, runProcess, exitCode)
        }
      }

      /** prepare our http response */
      prepareHttpResponse(jobId, workingDir, httpRequest, runProcess, processLogger, exitCodeFuture)
    }

    protected def invokeJob(jobId: JobId, workingDir: Path, runProcess: RunProcess, processLogger: ProcessLoggers)(
        implicit ec: ExecutionContext): Future[Int] = {
      processLogger.exitCodeFuture.onComplete {
        case tri => eventMonitor.accept(CompletedJob(jobId, tri))
      }

      val localRunner = LocalRunner(workDir = Option(workingDir))
      localRunner.execute(runProcess, processLogger)
    }

    /**
      * Here additional parameters are given to aid in potential subclassing
      *
      * @param jobId          the unique job id
      * @param workingDir     the directory the job is running in
      * @param httpRequest    the input request
      * @param runProcess     the job unmarshalled from the http request
      * @param processLogger  the loggers used in the running job
      * @param exitCodeFuture the future of the exit code
      * @return an HttpResposne
      */
    protected def prepareHttpResponse(
        jobId: JobId,
        workingDir: Path,
        httpRequest: HttpRequest,
        runProcess: RunProcess,
        processLogger: ProcessLoggers,
        exitCodeFuture: Future[Int])(implicit ec: ExecutionContext): Future[HttpResponse] = {

      formatHttpResponse(httpRequest, runProcess, processLogger)
    }

    protected def loggerForJob(runProcess: RunProcess,
                               detailsOpt: Option[MatchDetails],
                               workingDir: Path): ProcessLoggers = {
      val iterableLogger = IterableLogger(runProcess, detailsOpt)

      runProcess.output.stdOutFileName.foreach { stdOutFileName =>
        val stdOutLogger = ProcessLogger(workingDir.resolve(stdOutFileName).toFile)
        iterableLogger.addStdOut(stdOutLogger)
      }

      runProcess.output.stdErrFileName.foreach { stdErrFileName =>
        val stdErrLogger = ProcessLogger(workingDir.resolve(stdErrFileName).toFile)
        iterableLogger.addStdErr(stdErrLogger)
      }
      iterableLogger
    }
  }

  def asErrorResponse(exp: ProcessException) = {
    HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
  }

  def streamBytes(bytes: Source[ByteString, Any],
                  runProc: RunProcess,
                  matchDetails: Option[MatchDetails],
                  request: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] = {

    // TODO - extract from request header
    val outputContentType: ContentType = `text/plain(UTF-8)`

    val chunked: HttpEntity.Chunked  = HttpEntity(outputContentType, bytes)
    val future: Future[HttpResponse] = Marshal(chunked).toResponseFor(request)

    future.recover {
      case pr: ProcessException =>
        asErrorResponse(pr)
      case NonFatal(other) =>
        logger.error(s"translating error $other as a process exception")
        asErrorResponse(ProcessException(runProc, Failure(other), matchDetails, Nil))
    }
  }

  private def formatHttpResponse(httpRequest: HttpRequest, runProcess: RunProcess, processLogger: ProcessLoggers)(
      implicit ec: ExecutionContext): Future[HttpResponse] = {
    val basic = runProcess.output.streaming match {
      case Some(_) =>
        // TODO - this source will only run the iterator once, as it has potential side-effects.
        // we should check/challenge that
        val bytes: Source[ByteString, NotUsed] = {
          def run: Iterator[String] = processLogger.iterator

          Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
        }

        streamBytes(bytes, runProcess, processLogger.matchDetails, httpRequest)
      case None =>
        processLogger.fileResultFuture.flatMap { resp =>
          Marshal(resp).toResponseFor(httpRequest)
        }
    }

    /**
      * Put the match details back on the response for client consumption
      */
    processLogger.matchDetails.fold(basic) { details =>
      basic.map { r =>
        r.withHeaders(MatchDetailsExtractor.headersFor(details))
      }
    }
  }
}
