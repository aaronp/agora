package agora.exec.run

import agora.api.exchange.RequestWork
import agora.domain.IterableSubscriber
import agora.exec.model.RunProcess
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.exchange.ExchangeClient
import agora.rest.worker.WorkerClient
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}

case class RemoteRunner(exchange: ExchangeClient, defaultFrameLength: Int, allowTruncation: Boolean, requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  override def run(proc: RunProcess): ProcessOutput = {
    import agora.api.Implicits._
    import exchange.{execContext, materializer}
    import io.circe.generic.auto._

    /**
      * @see [[agora.exec.ExecutionHandler.prepareSubscription]] which adds criteria to match jobs w/ this key/value pair
      */
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
