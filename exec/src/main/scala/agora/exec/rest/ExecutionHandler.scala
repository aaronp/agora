package agora.exec.rest

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.exec.model.{ProcessException, RunProcess}
import agora.exec.run.LocalRunner
import agora.exec.workspace.WorkspaceId
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.Failure
import scala.util.control.NonFatal

private[rest] object ExecutionHandler extends StrictLogging {
  def newWorkspaceSubscription(workspace: WorkspaceId, files: Set[String]) = {
    WorkSubscription()
  }


  def asErrorResponse(exp: ProcessException) = {
    HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
  }

  def apply(request: HttpRequest,
            runner: LocalRunner,
            runProc: RunProcess,
            matchDetails: Option[MatchDetails],
            // TODO - this should be determined from the content-type of the request, which we have
            outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {

    val bytes = runner.asByteIterator(runProc)
    val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)
    val future = Marshal(chunked).toResponseFor(request)
    future.recover {
      case pr: ProcessException =>
        asErrorResponse(pr)
      case NonFatal(other) =>
        logger.error(s"translating error $other as a process exception")
        asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), matchDetails, Nil))
    }
  }

}
