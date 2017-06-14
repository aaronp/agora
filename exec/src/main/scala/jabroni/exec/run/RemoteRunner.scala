package jabroni.exec.run


import akka.http.scaladsl.model.HttpResponse
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import jabroni.api.exchange.RequestWork
import jabroni.domain.IterableSubscriber
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.exec.run.ProcessRunner.ProcessOutput
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartBuilder
import jabroni.rest.worker.WorkerClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.{Failure, Success}

case class RemoteRunner(exchange: ExchangeClient,
                        defaultFrameLength: Int,
                        allowTruncation: Boolean,
                        requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
  extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  /**
    * We've been notified of a job match and given an worker to execute it
    */
  def dispatchToWorker(worker: WorkerClient, proc: RunProcess, inputFiles: List[Upload]): Future[HttpResponse] = {
    import io.circe.generic.auto._

    logger.trace(s"preparing to send ${pprint.stringify(proc)}\nto\n${pprint.stringify(worker)}\nw/ ${inputFiles.size} uploads")
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
    import jabroni.api.Implicits._
    import exchange.execContext
    import exchange.materializer


    val (_, workerResponses) = exchange.enqueueAndDispatch(proc.asJob) { (worker: WorkerClient) =>
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