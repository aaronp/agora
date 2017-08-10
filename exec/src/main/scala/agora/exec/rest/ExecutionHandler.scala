package agora.exec.rest

import java.nio.file.Path

import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.api.json.JPath
import agora.api.nextJobId
import agora.api.worker.SubscriptionKey
import agora.exec.ExecConfig
import agora.exec.model.{ProcessException, RunProcess, RunProcessAndSave, RunProcessAndSaveResponse}
import agora.exec.run.LocalRunner
import agora.exec.workspace.{UploadDependencies, WorkspaceClient, WorkspaceId}
import agora.rest.MatchDetailsExtractor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger
import scala.util.{Failure, Properties}
import scala.util.control.NonFatal

object ExecutionHandler extends StrictLogging {

  def asErrorResponse(exp: ProcessException) = {
    HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
  }

  /** Execute 'runProc' unmarshalled from the request using the given runner and match details.
    *
    * @param request the input http request
    * @param runner the process runner
    * @param runProc the unmarshalled job request
    * @param matchDetails the match details if this request came via an exchange request
    * @param outputContentType the content type
    * @param ec an execution context used to map the response
    * @return the response future
    */
  def apply(request: HttpRequest,
            runner: LocalRunner,
            runProc: RunProcess,
            matchDetails: Option[MatchDetails],
            // TODO - this should be determined from the content-type of the request, which we have
            outputContentType: ContentType = `text/plain(UTF-8)`)(implicit ec: ExecutionContext): Future[HttpResponse] = {

    val bytes                       = runner.asByteIterator(runProc)
    val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)

    val future: Future[HttpResponse] = {
      val basic: Future[HttpResponse] = Marshal(chunked).toResponseFor(request)
      matchDetails.fold(basic) { details =>
        /*
         * Put the match details back on the response for client consumption
         */
        basic.map { r =>
          r.withHeaders(MatchDetailsExtractor.headersFor(details))
        }
      }
    }

    future.recover {
      case pr: ProcessException =>
        asErrorResponse(pr)
      case NonFatal(other) =>
        logger.error(s"translating error $other as a process exception")
        asErrorResponse(ProcessException(runProc, Failure(other), matchDetails, Nil))
    }
  }

  def executeAndSave(execConfig: ExecConfig, workspaces: WorkspaceClient, runProcess: RunProcessAndSave, detailsOpt: Option[MatchDetails]) = {

    /**
      * If the user hasn't explicitly specified a file dependency, we create one based on our workspaceId
      * in order to get a workDir
      */
    val uploadDependencies: UploadDependencies = runProcess.uploadDependencies

    val pathFuture = workspaces.await(uploadDependencies.workspace, uploadDependencies.dependsOnFiles, uploadDependencies.timeoutInMillis)

    pathFuture.flatMap { path =>
      executeToFile(execConfig, workspaces, runProcess, detailsOpt, Option(path))
    }
  }

  private def executeToFile(execConfig: ExecConfig, workspaces: WorkspaceClient, runProcess: RunProcessAndSave, detailsOpt: Option[MatchDetails], workingDir: Option[Path])(
      implicit ec: ExecutionContext): Future[RunProcessAndSaveResponse] = {

    val workspace: WorkspaceId = runProcess.workspaceId
    val outputFileName: String = runProcess.stdOutFileName
    val jobId                  = detailsOpt.map(_.jobId).getOrElse(nextJobId)

    import agora.io.implicits._
    val baseDir    = workingDir.getOrElse(Properties.userDir.asPath)
    val outputFile = baseDir.resolve(outputFileName)

    val runner = execConfig.newRunner(runProcess.asRunProcess, detailsOpt, workingDir, jobId).withLogger { jobLog =>
      val stdOutLogger = ProcessLogger(outputFile.toFile)
      jobLog.add(stdOutLogger)
    }

    runner.runAndSave(runProcess).map {
      case resultOhneMatchDetails =>
        val resp = resultOhneMatchDetails.copy(matchDetails = detailsOpt)

        logger.info(s"Triggering check for $outputFile after $jobId finished w/ exitCode ${resp.exitCode}")
        workspaces.triggerUploadCheck(workspace)
        resp
    }
  }

}
