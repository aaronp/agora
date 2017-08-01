package agora.exec.run

import agora.domain.IterableSubscriber
import agora.exec.model.RunProcess
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.exec.workspace.WorkspaceId
import agora.rest.exchange.ExchangeClient
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}
import io.circe.generic.auto._
import agora.api.Implicits._
import agora.api.exchange.SubmitJob
import agora.api.json.{JMatcher, JPart}

case class RemoteRunner(exchange: ExchangeClient,
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

    val job = RemoteRunner.prepare(proc, workspaceIdOpt, fileDependencies)

    val workerResponses = exchange.enqueue(job)

    val lineIterFuture = workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
    }

    lineIterFuture.map(proc.filterForErrors)
  }

  override def close(): Unit = exchange.close()
}

object RemoteRunner {

  /**
    * @see ExecutionHandler#newWorkspaceSubscription for the flip-side of this which prepares the work subscription
    *
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
