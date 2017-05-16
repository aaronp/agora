package jabroni.exec

import akka.stream.Materializer
import jabroni.domain.IterableSubscriber
import jabroni.exec.ProcessRunner.ProcessOutput
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartBuilder

import scala.concurrent.duration.FiniteDuration

import language.reflectiveCalls
import language.implicitConversions

case class RemoteRunner(exchange: ExchangeClient,
                        maximumFrameLength: Int,
                        allowTruncation: Boolean)(implicit mat: Materializer,
                                                  uploadTimeout: FiniteDuration)
  extends ProcessRunner
    with AutoCloseable {

  import mat._

  override def run(proc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
    import io.circe.generic.auto._

    val reqBuilder = inputFiles.foldLeft(MultipartBuilder().json(proc)) {
      case (builder, Upload(name, len, src)) =>
        builder.fromSource(name, len, src, fileName = name)
    }

    import jabroni.api.Implicits._
    val (_, workerResponses) = exchange.enqueueAndDispatch(proc.asJob) { worker =>
      reqBuilder.formData.flatMap(worker.sendMultipart)
    }

    workerResponses.map { completedWork =>
      val resp = completedWork.onlyResponse
      IterableSubscriber.iterate(resp.entity.dataBytes, maximumFrameLength, allowTruncation)
    }
  }

  override def close(): Unit = exchange.close()
}