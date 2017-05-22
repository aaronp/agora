package jabroni.exec

import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import jabroni.domain.IterableSubscriber
import jabroni.exec.ProcessRunner.ProcessOutput
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartBuilder
import jabroni.rest.worker.WorkerClient

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.Success

case class RemoteRunner(exchange: ExchangeClient,
                        defaultFrameLength: Int,
                        allowTruncation: Boolean)(implicit mat: Materializer,
                                                  uploadTimeout: FiniteDuration)
  extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport {

  import mat._

  override def run(proc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
    import io.circe.generic.auto._
    import jabroni.api.Implicits._

    /**
      * We've been notified of a job match and given an worker to execute it
      */
    def dispatchToWorker(worker: WorkerClient): Future[HttpResponse] = {
      val reqBuilder = inputFiles.foldLeft(MultipartBuilder().json(proc)) {
        case (builder, Upload(name, len, src)) =>
          builder.fromSource(name, len, src, fileName = name)
      }
      reqBuilder.formData.flatMap(worker.sendMultipart)
    }

    def cleanup(worker: WorkerClient) = {
      // TODO - remove resources when
    }

    val (_, workerResponses) = exchange.enqueueAndDispatch(proc.asJob) { worker =>
      val future = dispatchToWorker(worker)

      // cleanup in the case of success, leave results in the case of failure
      future.onComplete {
        case Success(httpResp) if httpResp.status.isSuccess() => cleanup(worker)
        case _ =>
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