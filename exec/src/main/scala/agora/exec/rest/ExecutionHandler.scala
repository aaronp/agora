package agora.exec.rest

import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.api.json.JPath
import agora.api.worker.SubscriptionKey
import agora.exec.model.{ProcessException, RunProcess}
import agora.exec.run.LocalRunner
import agora.exec.workspace.WorkspaceId
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
import scala.util.control.NonFatal

object ExecutionHandler extends StrictLogging {
  private val hasCommand = JPath("command").asMatcher

  /**
    * Creates a new WorkSubscription with:
    * {{{
    *   "workspace" : <xyz>
    *     "files: [...]
    * }}}
    * that also matches jobs with a 'command' in its json
    *
    * @param workspace
    * @param files
    * @return
    */
  def newWorkspaceSubscription(referencedConf : SubscriptionKey, workspace: WorkspaceId, files: Set[String]): WorkSubscription = {
    WorkSubscription(jobMatcher = hasCommand).withSubscriptionKey(workspace).append("files", files).append("workspace", workspace).referencing(referencedConf)
  }

  def asErrorResponse(exp: ProcessException) = {
    HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
  }

  def apply(request: HttpRequest,
            runner: LocalRunner,
            runProc: RunProcess,
            matchDetails: Option[MatchDetails],
            // TODO - this should be determined from the content-type of the request, which we have
            outputContentType: ContentType = `text/plain(UTF-8)`)(implicit ec: ExecutionContext): Future[HttpResponse] = {

    val bytes                       = runner.asByteIterator(runProc)
    val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)
    val future                      = Marshal(chunked).toResponseFor(request)
    future.recover {
      case pr: ProcessException =>
        asErrorResponse(pr)
      case NonFatal(other) =>
        logger.error(s"translating error $other as a process exception")
        asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), matchDetails, Nil))
    }
  }

}
