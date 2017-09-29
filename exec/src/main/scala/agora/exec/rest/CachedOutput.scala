package agora.exec.rest

import java.nio.file.Path

import agora.exec.model.{FileResult, ProcessException, RunProcess, StreamingSettings}
import agora.exec.workspace.WorkspaceId
import agora.io.implicits._
import agora.rest.MatchDetailsExtractor
import akka.NotUsed
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.AutoDerivation
import io.circe.syntax._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

/**
  * See [[agora.exec.model.OutputSettings]] for a description of caching strategy
  */
object CachedOutput extends FailFastCirceSupport with AutoDerivation {

  private[rest] val ExitCodeFileName = ".exitCode"
  private[rest] val StdOutFileName   = ".stdOutFileName"
  private[rest] val StdErrFileName   = ".stdErrFileName"
  private[rest] val JobFileName      = ".job"

  /**
    * given the working (workspace) directory which holds the user's files, the originating httpRequest, and the
    * inputProcess unmarshalled from that request, return an optionally cached future response.
    *
    * Note: If the cached exit code is not a successful one (according to the request) and it's a
    * streaming request, then the std error is returned.
    *
    * @param workingDir
    * @param httpRequest
    * @param inputProcess
    * @param ec
    * @return an optionally cached response
    */
  def cachedResponse(workingDir: Path, httpRequest: HttpRequest, inputProcess: RunProcess)(
      implicit ec: ExecutionContext): Option[Future[HttpResponse]] = {
    val dir = cacheDir(workingDir, inputProcess)

    val exitCodeFile = dir.resolve(ExitCodeFileName)
    if (exitCodeFile.exists) {
      val cachedExitCode = exitCodeFile.text.toInt
      inputProcess.output.streaming match {
        case None =>
          Option(asFileResultResponse(workingDir.fileName, dir, cachedExitCode, httpRequest))
        case Some(streamingSettings) =>
          asCachedStreamingResponse(workingDir, dir, streamingSettings, cachedExitCode, httpRequest, inputProcess)
      }
    } else {
      None
    }
  }

  private def asCachedStreamingResponse(
      workingDir: Path,
      cacheDir: Path,
      streamingSettings: StreamingSettings,
      cachedExitCode: Int,
      httpRequest: HttpRequest,
      inputProcess: RunProcess)(implicit ec: ExecutionContext): Option[Future[HttpResponse]] = {
    val successResult = streamingSettings.successExitCodes.contains(cachedExitCode)

    val stdOutFileName = cacheDir.resolve(StdOutFileName).text
    val stdErrFileName = cacheDir.resolve(StdErrFileName).text

    val stdOutFile = workingDir.resolve(stdOutFileName)
    val stdErrFile = workingDir.resolve(stdErrFileName)

    lazy val matchDetails = MatchDetailsExtractor.unapply(httpRequest)

    val stdOutBytesOpt = if (stdOutFile.exists) {
      Option(FileIO.fromPath(stdOutFile))
    } else {
      None
    }

    def asSrc(text: String) = Source.single(ByteString(text))

    val newLine = asSrc("\n")

    val stdErrBytesOpt: Option[Source[ByteString, NotUsed]] = if (stdErrFile.exists) {
      // TODO - put in err limit in request
      val stdErr     = stdErrFile.lines.take(1000)
      val exp        = ProcessException(inputProcess, Success(cachedExitCode), matchDetails, stdErr.toList)
      val respSource = asSrc(streamingSettings.errorMarker) ++ newLine ++ asSrc(exp.json.noSpaces)

      Option(respSource)
    } else {
      None
    }

    def streamResults(bytes: Source[ByteString, Any]) = {
      val resp: Future[HttpResponse] = ExecutionWorkflow.streamBytes(bytes, inputProcess, matchDetails, httpRequest)
      Option(resp)
    }

    (stdOutBytesOpt, stdErrBytesOpt) match {
      case (Some(out), _) if successResult => streamResults(out)
      case _ if successResult              => streamResults(Source.empty[ByteString])
      case (Some(out), Some(err))          => streamResults(out ++ newLine ++ err)
      case (Some(out), None)               => streamResults(out)
      case (None, Some(err))               => streamResults(err)
      case (None, None)                    => None
    }
  }

  private def asFileResultResponse(workspace: WorkspaceId,
                                   cacheDir: Path,
                                   cachedExitCode: Int,
                                   httpRequest: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] = {

    def asFileName(storedUnder: String) = {
      Option(cacheDir.resolve(storedUnder).text).filterNot(_.isEmpty)
    }

    val matchDetailsOpt = MatchDetailsExtractor.unapply(httpRequest)

    val result = FileResult(
      exitCode = cachedExitCode,
      workspaceId = workspace,
      stdOutFile = asFileName(StdOutFileName),
      stdErrFile = asFileName(StdErrFileName),
      matchDetails = matchDetailsOpt
    )

    Marshal(result).toResponseFor(httpRequest)
  }

  /**
    * @return the cache directory for the given [[RunProcess]] under the workspace (workdingDir)
    */
  def cacheDir(workingDir: Path, runProcess: RunProcess) = workingDir.resolve(".cache").resolve(runProcess.commandHash)

  /**
    * Save the runProcess data under the 'cacheDir'.
    *
    * This assumes [[RunProcess.ensuringCacheOutputs]] was called on the runProcess
    *
    * @param cacheDir   the <workspace>/.cache directory in which to write the data
    * @param runProcess the job to save
    * @param exitCode   the job's exit code
    */
  def cache(cacheDir: Path, runProcess: RunProcess, exitCode: Int): Unit = {
    cacheDir.resolve(ExitCodeFileName).text = exitCode.toString
    runProcess.output.stdOutFileName.foreach { fileName =>
      cacheDir.resolve(StdOutFileName).text = fileName
    }
    runProcess.output.stdErrFileName.foreach { fileName =>
      cacheDir.resolve(StdErrFileName).text = fileName
    }
    cacheDir.resolve(JobFileName).text = {
      runProcess.asJson.noSpaces
    }
  }

}
