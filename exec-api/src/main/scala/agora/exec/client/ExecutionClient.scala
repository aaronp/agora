package agora.exec.client

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.exec.model.{FileResult, OperationResult, RunProcess, RunProcessResult}
import agora.rest.CommonRequestBuilding
import agora.rest.client.RestClient
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, reflectiveCalls}

/**
  * A client of the ExecutionRoutes and UploadRoutes
  *
  * @param client
  */
case class ExecutionClient(override val client: RestClient,
                           defaultFrameLength: Int,
                           matchDetails: Option[MatchDetails] = None)(implicit uploadTimeout: FiniteDuration)
    extends UploadClient
    with FailFastCirceSupport
    with AutoCloseable {

  import client.{executionContext, materializer}

  /**
    * Execute the request
    *
    * @param proc
    * @return the http response whose entity body contains the process output
    */
  def execute(proc: RunProcess): Future[HttpResponse] = {
    val request = ExecutionClient.asRequest(proc, matchDetails)
    client.send(request)
  }

  def cancel(jobId: JobId, waitFor: String = ""): Future[Option[Boolean]] = {
    cancelHttp(jobId, waitFor).flatMap { resp =>
      ExecutionClient.parseCancelResponse(resp)
    }
  }

  def cancelHttp(jobId: JobId, waitFor: String = ""): Future[HttpResponse] = {
    val request: HttpRequest = ExecutionClient.asCancelRequest(jobId, waitFor)
    client.send(request)
  }

  /**
    * like execute, but returns a user-friendly return value
    *
    * @param proc the process to run
    * @return the future of the process output
    */
  def run(proc: RunProcess): Future[RunProcessResult] = {
    execute(proc).flatMap { httpResp =>
      proc.output.streaming match {
        case Some(streamingSettings) =>
          val result = streamingSettings.asResult(httpResp, defaultFrameLength)
          Future.successful(result)
        case None =>
          import io.circe.generic.auto._
          Unmarshal(httpResp).to[FileResult]
      }
    }
  }

  final def run(cmd: String, theRest: String*): Future[RunProcessResult] = run(RunProcess(cmd :: theRest.toList))

  override def close(): Unit = client.close()
}

object ExecutionClient extends CommonRequestBuilding with FailFastCirceSupport {

  def parseCancelResponse(resp: HttpResponse)(implicit mat: Materializer): Future[Option[Boolean]] = {
    import mat._
    if (resp.status == StatusCodes.NotFound) {
      Future.successful(None)
    } else {
      Unmarshal(resp).to[Json].map(_.asBoolean)
    }
  }

  def asRequest(job: RunProcess, matchDetails: Option[MatchDetails] = None)(implicit ec: ExecutionContext) = {
    Post("/rest/exec/run", HttpEntity(ContentTypes.`application/json`, job.asJson.noSpaces))
      .withCommonHeaders(matchDetails)
  }

  def asCancelRequest(jobId: JobId, waitFor: String = "")(implicit ec: ExecutionContext) = {
    val query = waitFor match {
      case ""         => Query("jobId" -> jobId)
      case timeToWait => Query("jobId" -> jobId, "waitFor" -> timeToWait)
    }
    Delete(Uri("/rest/exec/cancel").withQuery(query))
  }
}
