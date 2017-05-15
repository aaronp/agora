package jabroni.exec

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import jabroni.domain.io.Sources
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}
import jabroni.rest.worker.WorkContext

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object ExecutionWorker extends StrictLogging {

  def onRun(runner: ProcessRunner,
            req: WorkContext[MultipartPieces],
            uploadTimeout: FiniteDuration,
            outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {
    import req.requestContext._

    val uploadFutures = req.request.collect {
      case (MultipartInfo(_, Some(fileName), _), src) =>
        Sources.sizeOf(src).map { len =>
          Upload(fileName, len, src)
        }
    }
    val runProcFuture: Future[RunProcess] = req.multipartJson[RunProcess]
    for {
      runProc <- runProcFuture
      uploads <- Future.sequence(uploadFutures.toList)
    } yield {
      def run = Await.result(runner.run(runProc, uploads), uploadTimeout)

      val src = Source.fromIterator(() => run)
      val bytes = src.map(line => ByteString(s"$line\n"))

      HttpResponse(entity = HttpEntity(outputContentType, bytes))
    }
  }
}
