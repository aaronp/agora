package agora.exec.rest

import agora.api.`match`.MatchDetails
import agora.api.nextJobId
import agora.exec.ExecConfig
import agora.exec.model._
import agora.exec.run.LocalRunner
import agora.exec.workspace.WorkspaceClient
import agora.rest.MatchDetailsExtractor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger
import scala.util.Failure
import scala.util.control.NonFatal

object ExecutionHandler extends StrictLogging {

  def asErrorResponse(exp: ProcessException) = {
    HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
  }

  /** Execute 'runProc' unmarshalled from the request using the given runner and match details.
    *
    * @param request           the input http request
    * @param runner            the process runner
    * @param runProc           the unmarshalled job request
    * @param matchDetails      the match details if this request came via an exchange request
    * @param outputContentType the content type
    * @param ec                an execution context used to map the response
    * @return the response future
    */
  def apply(request: HttpRequest,
            runner: LocalRunner,
            runProc: StreamingProcess,
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

  /**
    * Execute the process using the stuff you give to this function. I'd like to thank you for taking the time to
    * read that informative comment.
    *
    */
  def executeAndSave(execConfig: ExecConfig, workspaces: WorkspaceClient, runProcess: ExecuteProcess, detailsOpt: Option[MatchDetails])(
      implicit ec: ExecutionContext): Future[ResultSavingRunProcessResponse] = {

    /**
      * If the user hasn't explicitly specified a file dependency, we create one based on our workspaceId
      * in order to get a workDir
      */
    val pathFuture = workspaces.await(runProcess.uploadDependencies)

    pathFuture.flatMap { workingDir =>
      val jobId = detailsOpt.map(_.jobId).getOrElse(nextJobId)

      val runner = execConfig.newRunner(runProcess.asStreamingProcess, detailsOpt, Option(workingDir), jobId).withLogger { jobLog =>
        val stdOutLogger = ProcessLogger(workingDir.resolve(runProcess.stdOutFileName).toFile)
        jobLog.addStdOut(stdOutLogger)

        val stdErrLogger = ProcessLogger(workingDir.resolve(runProcess.stdErrFileName).toFile)
        jobLog.addStdErr(stdErrLogger)
      }

      runner.runAndSave(runProcess).map { resultOhneMatchDetails =>
        val resp = resultOhneMatchDetails.copy(matchDetails = detailsOpt)

        logger.info(s"Triggering check for $workingDir after $jobId finished w/ exitCode ${resp.exitCode}")
        workspaces.triggerUploadCheck(runProcess.workspaceId)
        resp
      }
    }
  }

}
