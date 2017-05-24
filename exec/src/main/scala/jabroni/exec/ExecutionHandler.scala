package jabroni.exec

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import jabroni.domain.io.Sources
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}
import jabroni.rest.worker.WorkContext

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.control.NonFatal

trait ExecutionHandler {

  def onExecute(req: WorkContext[MultipartPieces]): Unit
}

object ExecutionHandler {

  def apply(execConfig: ExecConfig) = new Instance(execConfig)

  class Instance(val execConfig: ExecConfig) extends ExecutionHandler with StrictLogging {

    import execConfig.implicits._

    def asErrorResponse(exp: ProcessException) = {
      HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
    }

    override def onExecute(req: WorkContext[MultipartPieces]) = {
      val runner = execConfig.newRunner(req)
      val handlerFuture = onRun(runner, req, execConfig.uploadTimeout).recover {
        case pr: ProcessException =>
          asErrorResponse(pr)
        case NonFatal(other) =>
          logger.error(s"translating error $other as a process exception")
          asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), Nil))
      }
      req.completeWith(handlerFuture)
    }

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

      val runProcFuture: Future[RunProcess] = {
        import io.circe.generic.auto._
        req.multipartJson[RunProcess]
      }

      def marshalResponse(runProc: RunProcess, uploads: List[Upload]) = {
        def run = {
          try {
            Await.result(runner.run(runProc, uploads), uploadTimeout)
          } catch {
            case NonFatal(err) =>
              logger.error(s"Error executing $runProc: $err")
              throw err
          }
        }

        val bytes = Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
        val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)
        Marshal(chunked).toResponseFor(req.requestContext.request)
      }

      for {
        runProc <- runProcFuture
        uploads <- FastFuture.sequence(uploadFutures.toList)
        resp <- marshalResponse(runProc, uploads)
      } yield {
        resp
      }
    }
  }

}