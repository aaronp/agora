package agora.exec.session

import agora.api.Implicits._
import agora.api.JobId
import agora.api.exchange.SubmitJobResponse
import agora.domain.IterableSubscriber
import agora.exec.model.{RunProcess, Upload}
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.exchange.ExchangeClient
import agora.rest.multipart.MultipartBuilder
import agora.rest.worker.WorkerClient
import akka.http.scaladsl.model.HttpResponse
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/**
  *
  * @param exchange
  * @param frameLength
  * @param allowTruncation
  */
class RemoteSessionRunner(exchange: ExchangeClient, frameLength: Int, allowTruncation: Boolean)(implicit timeout: FiniteDuration)
    extends SessionRunner
    with StrictLogging
    with AutoCloseable
    with FailFastCirceSupport {
  import exchange.execContext
  import exchange.materializer

  override def startSession(id: SessionId): Future[JobId] = {
    val job = OpenSession.asJob(id)
    exchange.submit(job).mapTo[SubmitJobResponse].map(_.id)
  }

  override def closeSession(id: SessionId) = {
    val job = CloseSession.asJob(id)
    exchange.submit(job).mapTo[SubmitJobResponse].map(_.id)
  }

  override def upload(id: SessionId, inputFiles: List[Upload]): Future[Boolean] = {
    val job = UseSession.asUploadJob(id)

    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { (worker: WorkerClient) =>
      dispatchToWorker(worker, inputFiles)
    }

    workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      resp.status.isSuccess()
    }
  }

  override def run(id: SessionId, proc: RunProcess, uploadDependencies: Set[String]): ProcessOutput = {
    val job                                       = UseSession.asExecJob(id, proc)
    val workerResponses: exchange.WorkerResponses = exchange.enqueue(job)

    val lineIterFuture = workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, frameLength, allowTruncation)
    }

    lineIterFuture.map(proc.filterForErrors)
  }

  private def dispatchToWorker(worker: WorkerClient, inputFiles: List[Upload]): Future[HttpResponse] = {
    logger.trace(s"preparing to send ${pprint.stringify(worker)} ${inputFiles.size} uploads")
    val reqBuilder: MultipartBuilder = inputFiles.foldLeft(MultipartBuilder()) {
      case (builder, Upload(name, len, src, contentType)) =>
        builder.fromSource(name, len, src, contentType, name)
    }
    worker.send(reqBuilder)
  }

  override def close(): Unit = exchange.close()
}
