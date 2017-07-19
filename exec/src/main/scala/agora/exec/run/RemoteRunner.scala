package agora.exec.run

import akka.http.scaladsl.model.HttpResponse
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import agora.api.exchange.RequestWork
import agora.domain.IterableSubscriber
import agora.exec.model.{RunProcess, Upload}
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.exchange.ExchangeClient
import agora.rest.multipart.MultipartBuilder
import agora.rest.worker.WorkerClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.{Failure, Success}

case class RemoteRunner(exchange: ExchangeClient, defaultFrameLength: Int, allowTruncation: Boolean, requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  /**
    * We've been notified of a job match and given an worker to execute it
    */
  def dispatchToWorker(worker: WorkerClient, proc: RunProcess, inputFiles: List[Upload]): Future[HttpResponse] = {
    import io.circe.generic.auto._

    logger.trace(s"preparing to send ${pprint.apply(proc)}\nto\n${pprint.apply(worker)}\nw/ ${inputFiles.size} uploads")
    val reqBuilder: MultipartBuilder = inputFiles.foldLeft(MultipartBuilder().json(proc)) {
      case (builder, Upload(name, len, src, contentType)) =>
        builder.fromSource(name, len, src, contentType, name)
    }
    worker.send(reqBuilder)
  }

  def cleanup(worker: WorkerClient) = {
    // TODO - remove resources when
  }

  def replaceWorkOnFailure(worker: WorkerClient) = {
    if (requestWorkOnFailure) {
      logger.debug(s"Requesting a work-item on behalf of our worker using ${worker.workerDetails.subscriptionKey}")
      worker.workerDetails.subscriptionKey.foreach { key =>
        exchange.take(RequestWork(key, 1))
      }
    }
  }

  override def run(proc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
    import io.circe.generic.auto._
    import agora.api.Implicits._
    import exchange.execContext
    import exchange.materializer

    /**
      * @see [[agora.exec.ExecutionHandler.prepareSubscription]] which adds criteria to match jobs w/ this key/value pair
      */
    val job = proc.asJob.add("topic" -> "exec")

    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { (worker: WorkerClient) =>
      val future = dispatchToWorker(worker, proc, inputFiles)

      // cleanup in the case of success, leave results in the case of failure
      future.onComplete {
        case Success(httpResp) if httpResp.status.isSuccess() => cleanup(worker)
        case Success(httpResp) =>
          logger.debug(s"Received a non-success response from $worker: ${httpResp}")
          replaceWorkOnFailure(worker)
        case Failure(err) =>
          logger.warn(s"Received an error from $worker : ${err}")
          replaceWorkOnFailure(worker)
      }
      future
    }

    val lineIterFuture = workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
    }

    lineIterFuture.map(proc.filterForErrors)
  }

  override def close(): Unit = exchange.close()
}
