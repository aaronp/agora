package agora.exec.client

import agora.exec.model.{FileResult, RunProcess, RunProcessResult}
import agora.io.IterableSubscriber
import agora.rest.client.RestClient
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.unmarshalling.Unmarshal
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, reflectiveCalls}

import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.java8.time._

/**
  * A client of the ExecutionRoutes and UploadRoutes
  *
  * @param client
  */
case class ExecutionClient(override val client: RestClient, defaultFrameLength: Int)(
    implicit uploadTimeout: FiniteDuration)
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
    client.send(ExecutionClient.asRequest(proc))
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

object ExecutionClient extends RequestBuilding {
  def asRequest(job: RunProcess)(implicit ec: ExecutionContext) = {
    Post("/rest/exec/run", HttpEntity(ContentTypes.`application/json`, job.asJson.noSpaces))
  }
}
