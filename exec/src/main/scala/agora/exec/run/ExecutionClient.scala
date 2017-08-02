package agora.exec.run

import agora.api.Implicits._
import agora.api.exchange.SubmitJob
import agora.api.json.JMatcher
import agora.domain.IterableSubscriber
import agora.exec.model.RunProcess
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.exec.workspace.WorkspaceId
import agora.rest.exchange.ExchangeClient
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}

/**
  * A client of the ExecutionRoutes
  * @param exchange
  * @param workspaceIdOpt
  * @param fileDependencies
  * @param defaultFrameLength
  * @param allowTruncation
  * @param requestWorkOnFailure
  * @param uploadTimeout
  */
case class ExecutionClient(exchange: ExchangeClient,
                           workspaceIdOpt: Option[WorkspaceId],
                           fileDependencies: Set[String],
                           defaultFrameLength: Int,
                           allowTruncation: Boolean,
                           requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  override def run(proc: RunProcess): ProcessOutput = {
    import exchange.{execContext, materializer}

    val job = ExecutionClient.prepare(proc, workspaceIdOpt, fileDependencies)

    val workerResponses = exchange.enqueue(job)

    val lineIterFuture = workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
    }

    lineIterFuture.map(proc.filterForErrors)
  }

  def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any], contentType: ContentType = ContentTypes.`text/plain(UTF-8)`) = {
    val job = {
      val baseJob = "upload".asJob
      workspaceIdOpt.fold(baseJob) { workspace =>
        baseJob.add("workspace", workspace)
      }
    }
    exchange.enqueueAndDispatch(job) { worker =>
      val request = ExecutionClient.asRequest(workspaceId, fileName, src, contentType)
      worker.send(request)
    }
  }

  override def close(): Unit = {
    ???
    workspaceIdOpt.foreach { workspace =>
      }
    exchange.close()
  }
}

object ExecutionClient extends RequestBuilding {

  def asRequest(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any], contentType: ContentType): HttpRequest = {
    val chunk = HttpEntity(contentType, src).withContentType(contentType)
    //    val query = ("filename", fileName) +: Query(s"workspace=$workspaceId")
    val query = Query(s"workspace=$workspaceId")
    val uri   = Uri("/rest/exec/upload").withQuery(query)
    Post(uri, chunk).withHeaders(`Content-Disposition`(ContentDispositionTypes.`form-data`, Map("filename" -> fileName)))
  }

  /**
    * @see ExecutionHandler#newWorkspaceSubscription for the flip-side of this which prepares the work subscription
    * @param runProcess
    * @param workspaceIdOpt
    * @param fileDependencies
    * @return
    */
  def prepare(runProcess: RunProcess, workspaceIdOpt: Option[WorkspaceId], fileDependencies: Set[String]): SubmitJob = {
    //    import agora.api.json.JPredicate.implicits._
    import agora.api.Implicits._

    val subscriptionMatcher: JMatcher = workspaceIdOpt match {
      case Some(workspace) if fileDependencies.nonEmpty =>
        val hasFiles          = "files".includes(fileDependencies)
        val matchesWorkspace  = "workspace" === workspace
        val matcher: JMatcher = hasFiles.and(matchesWorkspace)
        matcher
      case Some(workspace) => ("workspace" === workspace).asMatcher
      case None            => JMatcher.matchAll
    }

    runProcess.asJob.matching(subscriptionMatcher)
  }
}
