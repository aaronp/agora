package agora.exec.rest

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.exec.client.{AsJProcess, LocalRunner}
import agora.exec.events.{CompletedJob, ReceivedJob, StartedJob, SystemEventMonitor}
import agora.exec.log.{IterableLogger, ProcessLoggers}
import agora.exec.model._
import agora.exec.workspace.WorkspaceClient
import agora.rest.MatchDetailsExtractor
import akka.NotUsed
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._

import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Represents a handler which will be triggered from the given [[HttpRequest]] when a [[RunProcess]] is received
  *
  */
trait ExecutionWorkflow extends AutoCloseable {

  /**
    * Cancel the job identified by the job ID
    *
    * @param jobId   the job to cancel
    * @param waitFor if non-zero, then we will awaitWorkspace for the given duration for the job to exit before returning
    * @return an HttpResponse to the cance request
    */
  def onCancelJob(jobId: JobId, waitFor: FiniteDuration = 0.millis)(implicit ec: ExecutionContext): Future[HttpResponse]

  /**
    * The job is received. Do something with it and reply...
    *
    * @param httpRequest  the originating http request
    * @param inputProcess the unmarshalled input process
    * @return the eventual [[HttpResponse]]
    */
  def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse]

  /**
    * Provides a hook for workflows to 'fix up' or otherwise alter the user input
    * requests sent -- e.g. by injecting environment variables and some such
    *
    * @param inputProcess
    * @return the resolved request
    */
  def resolveUserRequest(inputProcess: RunProcess): RunProcess = inputProcess
}

case object ExecutionWorkflow extends StrictLogging with FailFastCirceSupport {

  /**
    * A workflow which will check .cache directories in the request's workspace
    *
    * @param workspaces
    * @param underlying
    */
  class CachingWorkflow(workspaces: WorkspaceClient, underlying: ExecutionWorkflow) extends ExecutionWorkflow {

    override def close(): Unit = {
      underlying.close()
    }

    override def onCancelJob(jobId: JobId, waitFor: FiniteDuration)(implicit ec: ExecutionContext) = {
      underlying.onCancelJob(jobId, waitFor)
    }

    override def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse] = {
      if (inputProcess.output.useCachedValueWhenAvailable) {
        checkCache(httpRequest, inputProcess)
      } else {
        processUncached(httpRequest, inputProcess)
      }
    }

    protected def processUncached(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext) = {
      underlying.onExecutionRequest(httpRequest, inputProcess)
    }

    // 'awaitWorkspace' a no-op dependency to get the working directory
    private def workDirFuture(inputProcess: RunProcess): Future[Path] = {
      workspaces.awaitWorkspace(inputProcess.dependencies.copy(dependsOnFiles = Set.empty, timeoutInMillis = 0))
    }

    override def resolveUserRequest(inputProcess: RunProcess) = {
      underlying.resolveUserRequest(inputProcess)
    }

    def checkCache(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse] = {

      import akka.http.scaladsl.util.FastFuture._

      val resolvedProcess = resolveUserRequest(inputProcess)

      workDirFuture(resolvedProcess).fast.flatMap { workingDir =>
        val cacheOpt = CachedOutput.cachedResponse(workingDir, httpRequest, resolvedProcess)

        cacheOpt match {
          case Some((entry, cachedResponse)) =>
            // there was a cached entry. If the user has specified to run their job w/
            // std out or std err outputs, we should link to the cached files

            if (entry.createCacheLinks()) {
              workspaces.triggerUploadCheck(resolvedProcess.workspace)
            }

            cachedResponse
          case None => processUncached(httpRequest, inputProcess)
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
    * 1) awaitWorkspace any dependencies declared on the [[RunProcess]] using the [[WorkspaceClient]]
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
  class Instance(val defaultEnv: Map[String, String], val workspaces: WorkspaceClient, val eventMonitor: SystemEventMonitor, cacheEnabled: Boolean)
      extends ExecutionWorkflow
      with FailFastCirceSupport {

    override def close(): Unit = {
      eventMonitor.close()
    }

    /**
      * Used to track processes for cancellation, protected by 'ProcessLock'
      */
    private var processById = Map[JobId, Process]()

    private object ProcessLock

    override def onCancelJob(jobId: JobId, waitFor: FiniteDuration)(implicit ec: ExecutionContext) = {

      val responseOpt: Option[Try[Process]] = {
        val procOpt = ProcessLock.synchronized {
          processById.get(jobId).map { process =>
            processById = processById - jobId
            process
          }
        }

        procOpt.map { process =>
          logger.info(s"Cancelling $jobId, there are ${processById.size} jobs running")
          Try {
            // we want to always invoke destroy on the scala process, as it will also destroy other resources
            // associated w/ the process. We then try to destroy forcibly if we can
            process.destroy()
            process match {
              case AsJProcess(jProcess) => jProcess.destroyForcibly()
              case _                    =>
            }
            process
          }
        }
      }

      import io.circe.generic.auto._
      import io.circe.syntax._

      responseOpt match {
        case Some(Success(process)) =>
          Future {
            def waitForCancel: Boolean = process match {
              case AsJProcess(jProcess) => jProcess.waitFor(waitFor.toMillis, TimeUnit.MILLISECONDS)
              case _                    => true
            }

            val cancelOk: Boolean = waitFor.toMillis <= 0 || waitForCancel
            HttpResponse(StatusCodes.OK)
              .withEntity(ContentTypes.`application/json`, Json.fromBoolean(cancelOk).noSpaces)
          }
        case Some(Failure(err)) =>
          Future.successful {
            HttpResponse(StatusCodes.InternalServerError)
              .withEntity(ContentTypes.`application/json`, OperationResult(s"Error canceling $jobId", err.getMessage).asJson.noSpaces)
          }
        case None =>
          val json     = OperationResult(s"Couldn't find job $jobId").asJson.noSpaces
          val response = HttpResponse(StatusCodes.NotFound).withEntity(ContentTypes.`application/json`, json)
          Future.successful(response)
      }

    }

    override def onExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse] = {
      val started = Platform.currentTime
      val future  = handleExecutionRequest(httpRequest, inputProcess)
      future.onComplete {
        case tri =>
          // we may have produced some side-effecting output, so we should trigger a check
          workspaces.triggerUploadCheck(inputProcess.workspace)

          val res = tri match {
            case Success(resp) => s"with ${resp.status}"
            case Failure(_)    => "in error"
          }
          val took = Platform.currentTime - started
          logger.info(s"${inputProcess.commandString} completed ${res} after ${took}ms")
      }

      future
    }

    private def handleExecutionRequest(httpRequest: HttpRequest, inputProcess: RunProcess)(implicit ec: ExecutionContext): Future[HttpResponse] = {

      /** 1) Add any system (configuration) wide environment properties to the input request */
      val runProcess: RunProcess = {
        val withEnv = resolveUserRequest(inputProcess)
        logger.trace(s"""Added input process env
             |${inputProcess.env}
             |to default env 
             |${defaultEnv}
             |to produce 
             |${withEnv}
             |for
             |${inputProcess.commandString}
           """.stripMargin)

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
      val receivedEvent = ReceivedJob(jobId, detailsOpt, inputProcess)
      eventMonitor.accept(receivedEvent)

      val httpPromise = Promise[HttpResponse]()

      def continueWithWorkspace(workspaceDir: Path) = {
        logger.debug(s"Workspace '${runProcess.dependencies.workspace}' completed for ${runProcess.dependencies.dependsOnFiles} w/ $workspaceDir")

        val startedEvent = StartedJob(jobId)
        eventMonitor.accept(startedEvent)
        val httpFuture: Future[HttpResponse] = onJob(httpRequest, workspaceDir, jobId, detailsOpt, runProcess)
        httpPromise.tryCompleteWith(httpFuture)
      }

      /** 4) obtain a workspace in which to run the job
        * this may eventually time-out based on the workspaces configuration
        */
      val workspaceFuture: Future[Path] = workspaces.awaitWorkspace(runProcess.dependencies)

      val futId = System.identityHashCode(workspaceFuture)
      logger.warn(
        s"Workspace '${runProcess.dependencies.workspace}' (futId $futId) created for cmd >${runProcess.commandString}< w/ ${runProcess.dependencies.dependsOnFiles}")

      workspaceFuture.onComplete { workspaceTry: Try[Path] =>
        workspaceTry match {
          case Success(workspaceDir) =>
            continueWithWorkspace(workspaceDir)
          case Failure(err) =>
            val completed = httpPromise.tryFailure(err)
            logger.error(
              s"Workspace '${runProcess.dependencies.workspace}' future $workspaceFuture failed for ${runProcess.dependencies.dependsOnFiles} w/ $err, completedPromise=$completed")
        }

      }

      httpPromise.future
    }

    /**
      * Resolves dollar-delimited references in the command string based on the
      * input and default environment variables
      */
    override def resolveUserRequest(inputProcess: RunProcess): RunProcess = {
      inputProcess.withEnv(defaultEnv ++ inputProcess.env).resolveEnv
    }

    protected def onJob(httpRequest: HttpRequest, workingDir: Path, jobId: JobId, detailsOpt: Option[MatchDetails], runProcess: RunProcess)(
        implicit ec: ExecutionContext): Future[HttpResponse] = {
      val processLogger: ProcessLoggers = loggerForJob(jobId: JobId, runProcess, detailsOpt, workingDir)

      /** actually execute the [[RunProcess]] and return the Future[Int] of the exit code */
      val exitCodeFuture: Future[Int] = invokeJob(jobId, workingDir, runProcess, processLogger)

      /** If our job writes to a file, we should trigger a workspace check when that job exits */
      if (runProcess.hasFileOutputs) {
        exitCodeFuture.onComplete {
          case _ =>
            runProcess.output.stdOutFileName match {
              case Some(stdOutFile) =>
                val written = processLogger.stdOutBytesWritten
                workspaces.markComplete(runProcess.workspace, Map(stdOutFile -> written))
              case None => workspaces.triggerUploadCheck(runProcess.workspace)
            }
        }
      }

      /**
        * If we can cache (and we got this far ... hence implying we've not already been able to take advantage of a
        * cached value)
        */
      if (cacheEnabled && runProcess.output.canCache) {
        exitCodeFuture.onComplete {
          case Failure(err) =>
            logger.error(s"Won't cache as process exited in error: $err")
          case Success(exitCode) =>
            val cacheDir = CachedOutput.cacheDir(workingDir, runProcess)
            logger.debug(s"Caching results $exitCode under $cacheDir for ${runProcess.commandString}")
            try {
              CachedOutput.cache(cacheDir, runProcess, exitCode)
            } catch {
              case NonFatal(e) =>
                logger.error(s"Error '${e.getMessage}' trying to cache result under ${cacheDir} for $runProcess", e)
            }
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
      val processTry  = localRunner.startProcess(runProcess, processLogger)

      logger.info(s"Started '$jobId' >${runProcess.commandString}< w/ $processTry")

      processTry.toOption.foreach { process =>
        ProcessLock.synchronized {

          // TODO - we need to raise an issue to document/control this workflow... what happens when
          // the same jobId is started twice?
          if (processById.contains(jobId)) {
            logger.error(s"Job '$jobId' is already running !")
          } else {
            processById = processById.updated(jobId, process)
            logger.info(s"Job '$jobId' started, now tracking ${processById.size} running jobs")
          }
        }
      }

      val resultFuture = localRunner.execute(runProcess, processLogger, processTry)

      resultFuture.onComplete {
        case _ =>
          ProcessLock.synchronized {
            processById = processById - jobId
            logger.debug(s"Job $jobId completed, now tracking ${processById.size} running jobs")
          }
      }
      resultFuture
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
      * @return an HttpResponse
      */
    protected def prepareHttpResponse(jobId: JobId,
                                      workingDir: Path,
                                      httpRequest: HttpRequest,
                                      runProcess: RunProcess,
                                      processLogger: ProcessLoggers,
                                      exitCodeFuture: Future[Int])(implicit ec: ExecutionContext): Future[HttpResponse] = {

      formatHttpResponse(httpRequest, runProcess, processLogger)
    }

    protected def loggerForJob(jobId: JobId, runProcess: RunProcess, detailsOpt: Option[MatchDetails], workingDir: Path): ProcessLoggers = {
      val iterableLogger = IterableLogger(runProcess, detailsOpt)

      def addLogger(onLine: String => Unit) = {
        val prefix = jobId.size match {
          case n if n > 15 => jobId.take(10) + "... :"
          case _           => jobId + " :"
        }
        iterableLogger.add(ProcessLogger { out =>
          onLine(prefix + out)
        })
      }

      runProcess.output.logOutput.map(_.toLowerCase.trim).foreach {
        case "trace"            => addLogger(logger.trace(_: String))
        case "debug"            => addLogger(logger.debug(_: String))
        case "info"             => addLogger(logger.info(_: String))
        case "warn" | "warning" => addLogger(logger.warn(_: String))
        case "error"            => addLogger(logger.error(_: String))
      }

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

  /**
    * Stream the byte source into a chunked HttpResponse
    *
    * @param bytes
    * @param runProc
    * @param matchDetails
    * @param request
    * @param ec
    * @return
    */
  def streamBytes(bytes: Source[ByteString, Any], runProc: RunProcess, matchDetails: Option[MatchDetails], request: HttpRequest)(
      implicit ec: ExecutionContext): Future[HttpResponse] = {

    logger.debug(s"Streaming bytes for >${runProc.commandString}<")
    // TODO - extract from request header
    val outputContentType: ContentType = `text/plain(UTF-8)`

    val chunked: HttpEntity.Chunked  = HttpEntity(outputContentType, bytes)
    val future: Future[HttpResponse] = Marshal(chunked).toResponseFor(request)

    future.recover {
      case pr: ProcessException =>
        logger.error(s"creating error response for ${pr.error}")
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
