package agora.exec.rest

import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.api.json.JPath
import agora.api.worker.SubscriptionKey
import agora.exec.model.{ProcessException, RunProcess}
import agora.exec.run.LocalRunner
import agora.exec.workspace.WorkspaceId
import agora.rest.MatchDetailsExtractor
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpRequest, HttpResponse}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Failure
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

}
