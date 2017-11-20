package agora.exec.rest

import java.nio.file.Path

import agora.api.`match`.MatchDetails
import agora.exec.model.{FileResult, ProcessException, RunProcess, StreamingSettings}
import agora.exec.workspace.MetadataFile
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
    *
    * The [[HttpResponse]] will contain the headers 'x-cache-out' and 'x-cache-err' which will refer to the original
    * (cached) standard output and standard error results.
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
      implicit ec: ExecutionContext): Option[(CacheEntry, Future[HttpResponse])] = {

    val entry: CacheEntry = CacheEntry(workingDir, inputProcess)
    entry.cachedExitCode.flatMap { cachedExitCode =>
      val opt = inputProcess.output.streaming match {
        case None =>
          Option(entry.asFileResultResponse(cachedExitCode, httpRequest))
        case Some(streamingSettings) =>
          asCachedStreamingResponse(entry, streamingSettings, cachedExitCode, httpRequest)
      }
      opt.map { httpRespFuture =>
        import agora.rest.RestImplicits._
        import akka.http.scaladsl.util.FastFuture._
        val respWithHeaders: Future[HttpResponse] = httpRespFuture.fast.map { httpResp =>
          val cacheHeaders = {
            val outFileHeader = entry.stdOutFileName.map("x-cache-out".asHeader)
            val errFileHeader = entry.stdErrFileName.map("x-cache-err".asHeader)

            List(outFileHeader, errFileHeader).flatten
          }
          httpResp.withHeaders(cacheHeaders ++ httpResp.headers)
        }
        (entry, respWithHeaders)
      }
    }
  }

  private def asCachedStreamingResponse(cache: CacheEntry, streamingSettings: StreamingSettings, cachedExitCode: Int, httpRequest: HttpRequest)(
      implicit ec: ExecutionContext): Option[Future[HttpResponse]] = {
    val successResult = streamingSettings.successExitCodes.contains(cachedExitCode)

    val matchDetails = MatchDetailsExtractor.unapply(httpRequest)

    def streamResults(bytes: Source[ByteString, Any]) = {
      val resp: Future[HttpResponse] =
        ExecutionWorkflow.streamBytes(bytes, cache.inputProcess, matchDetails, httpRequest)
      Option(resp)
    }

    (cache.stdOutBytesOpt, cache.stdErrBytesOpt(streamingSettings, matchDetails)) match {
      case (Some(out), _) if successResult => streamResults(out)
      case _ if successResult              => streamResults(Source.empty[ByteString])
      case (Some(out), Some(err))          => streamResults(out ++ newLine ++ err)
      case (Some(out), None)               => streamResults(out)
      case (None, Some(err))               => streamResults(err)
      case (None, None)                    => None
    }
  }

  private def asSrc(text: String) = Source.single(ByteString(text))

  private val newLine = asSrc("\n")

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
    //cacheDir.resolve(ExitCodeFileName).createIfNotExists()

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

  private[rest] object CacheEntry {
    private[rest] def apply(workspaceDir: Path, inputProcess: RunProcess): CacheEntry = {
      val dir = cacheDir(workspaceDir, inputProcess)
      new CacheEntry(workspaceDir, dir, inputProcess)
    }

  }

  /**
    * A CacheEntry represents the contents of the '.cache' directory nested under a given workspace.
    *
    * @param workspaceDir the parent workspace directory
    * @param cacheDir
    * @param inputProcess
    */
  private[rest] case class CacheEntry private (workspaceDir: Path, cacheDir: Path, inputProcess: RunProcess) {

    def createCacheLinks(): Boolean = {
      val out = createStdOutLink()
      val err = createStdErrLink()
      out.isDefined || err.isDefined
    }

    private[rest] def asFileResultResponse(cachedExitCode: Int, httpRequest: HttpRequest)(implicit ec: ExecutionContext): Future[HttpResponse] = {

      val matchDetailsOpt = MatchDetailsExtractor.unapply(httpRequest)

      val result = FileResult(
        exitCode = cachedExitCode,
        workspaceId = inputProcess.workspace,
        stdOutFile = stdOutFileName.orElse(inputProcess.output.stdOutFileName),
        stdErrFile = stdErrFileName.orElse(inputProcess.output.stdErrFileName),
        matchDetails = matchDetailsOpt
      )

      Marshal(result).toResponseFor(httpRequest)
    }

    /**
      * If a job was run which cached its results under <workspacedir>/foo, then there will be an
      * entry:
      * {{{
      *   .cache/<JOB MD5 Hash>/.stdout = foo
      *
      * If we then run a job which specifies the output file 'bar', and it ends up using the 'foo' cache,
      * then we should create a link from:
      * {{{
      *   <workspacedir>/foo --> <workspacedir>/bar
      * }}}
      *
      * @return a linked file for the input process's std out file to the cached std out file
      */
    def createStdOutLink(): Option[Path] = createLink(stdOutFile, inputProcess.output.stdOutFileName)

    /**
      * @see createStdOutLink
      */
    def createStdErrLink(): Option[Path] = createLink(stdErrFile, inputProcess.output.stdErrFileName)

    private def createLink(cachedFileOpt: Option[Path], fileNameOpt: Option[String]): Option[Path] = {
      for {
        newName      <- fileNameOpt
        cachedOutput <- cachedFileOpt
        newPath = cachedOutput.getParent.resolve(newName)
        if !newPath.exists()
      } yield {

        /**
          * I believe we want to use hard links, as the stdout results returned from cached jobs should
          * remain even after the original may be deleted
          */
        val hardLink = cachedOutput.createHardLinkFrom(newPath)

        /**
          * We also want to link the '.<filename>.metadata' file
          */
        for {
          existingMdFile  <- MetadataFile.metadataFileForUpload(cachedOutput)
          newMetadataFile <- MetadataFile.metadataFileForUpload(newPath)
        } {
          existingMdFile.createHardLinkFrom(newMetadataFile)
        }

        hardLink
      }
    }

    def exists: Boolean = cachedExitCode.isDefined

    private def file(name: String) = Option(cacheDir.resolve(name)).filter(_.exists())

    private def text(name: String) = file(name).map(_.text)

    def exitCodeFile = file(ExitCodeFileName)

    def cachedExitCode: Option[Int] = text(ExitCodeFileName).map(_.toInt)

    def stdOutFileName: Option[String] = text(StdOutFileName)

    def stdErrFileName: Option[String] = text(StdErrFileName)

    def stdOutFile: Option[Path] = stdOutFileName.map(workspaceDir.resolve)

    def stdErrFile = stdErrFileName.map(workspaceDir.resolve)

    def stdOutBytesOpt = stdOutFile.map { path =>
      FileIO.fromPath(path)
    }

    def stdErrBytesOpt(streamingSettings: StreamingSettings, matchDetails: Option[MatchDetails]): Option[Source[ByteString, NotUsed]] =
      for {
        exitCode <- cachedExitCode
        errFile  <- stdErrFile
      } yield {
        val stdErr     = streamingSettings.errorLimit.fold(errFile.lines)(errFile.lines.take)
        val exp        = ProcessException(inputProcess, Success(exitCode), matchDetails, stdErr.toList)
        val respSource = asSrc(streamingSettings.errorMarker) ++ newLine ++ asSrc(exp.json.noSpaces)
        respSource
      }
  }

}
