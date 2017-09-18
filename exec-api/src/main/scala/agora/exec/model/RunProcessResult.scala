package agora.exec.model

import agora.api.`match`.MatchDetails
import agora.exec.workspace.WorkspaceId
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

/**
  * Depending on the output setting specified on the [[RunProcess]],
  * the runner may return either a [[StreamingResult]] or a [[FileResult]]
  */
sealed trait RunProcessResult

object RunProcessResult {
  def apply(iter: Iterator[String]) = StreamingResult(iter)

  def apply(exitCode: Int, workspaceId: WorkspaceId, stdOutFile: Option[String] = None, stdErrFile: Option[String] = None, matchDetails: Option[MatchDetails] = None) = {
    FileResult(exitCode, workspaceId, stdOutFile, stdErrFile, matchDetails)
  }
}

case class StreamingResult(output: Iterator[String]) extends RunProcessResult

case class FileResult(exitCode: Int, workspaceId: WorkspaceId, stdOutFile: Option[String] = None, stdErrFile: Option[String] = None, matchDetails: Option[MatchDetails] = None)
    extends RunProcessResult

object FileResult extends FailFastCirceSupport {
  def apply(resp: HttpResponse)(implicit mat: Materializer): Future[FileResult] = {
    import io.circe.generic.auto._
    Unmarshal(resp).to[FileResult]
  }
}
