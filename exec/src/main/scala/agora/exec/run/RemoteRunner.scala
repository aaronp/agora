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

case class RemoteRunner(exchange: ExchangeClient,
                        workspaceIdOpt : Option[WorkspaceId],
                        fileDependencies : Set[String],
                        defaultFrameLength: Int,
                        allowTruncation: Boolean,
                        requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  override def run(proc: RunProcess): ProcessOutput = {
    import agora.api.Implicits._
    import exchange.{execContext, materializer}
    import io.circe.generic.auto._

    // ???
    ???
    val job = proc.asJob.add("topic" -> "exec")

    val workerResponses = exchange.enqueue(job)

    val lineIterFuture = workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
    }

    lineIterFuture.map(proc.filterForErrors)
  }

  override def close(): Unit = exchange.close()
}
