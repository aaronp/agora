package jabroni.exec

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, Multipart}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.{JobId, nextJobId}
import jabroni.exec.rest.MultipartExtractor
import jabroni.rest.worker.WorkContext

import scala.concurrent.{Await, Future}
import scala.util.Failure
import scala.util.control.NonFatal

trait ExecutionHandler {

  def onExecute(req: WorkContext[Multipart.FormData]): Unit
}

object ExecutionHandler {

  def apply(execConfig: ExecConfig) = new Instance(execConfig)

  class Instance(val execConfig: ExecConfig) extends ExecutionHandler with StrictLogging {

    import execConfig.implicits._

    def asErrorResponse(exp: ProcessException) = {
      HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
    }

    override def onExecute(req: WorkContext[Multipart.FormData]) = {

      val jobId: JobId = req.matchDetails.map(_.jobId).getOrElse(nextJobId)
      val runner = execConfig.newRunner(req, jobId)
      val handlerFuture = onRun(runner, req, jobId).recover {
        case pr: ProcessException =>
          asErrorResponse(pr)
        case NonFatal(other) =>
          logger.error(s"translating error $other as a process exception")
          asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), req.matchDetails, Nil))
      }
      req.completeWith(handlerFuture)
    }


    def onRun(runner: ProcessRunner,
              ctxt: WorkContext[Multipart.FormData],
              jobId: JobId,
              // TODO - this should be determined from the content-type of the request, which we have
              outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {
      import ctxt.requestContext._
      import jabroni.rest.multipart.MultipartFormImplicits._

      val uploadFutures: Future[(RunProcess, List[Upload])] = MultipartExtractor(execConfig.uploads, ctxt, jobId, execConfig.chunkSize)

      def marshalResponse(runProc: RunProcess, uploads: List[Upload]): Future[HttpResponse] = {
        def run = {
          try {
            Await.result(runner.run(runProc, uploads), execConfig.uploadTimeout)
          } catch {
            case NonFatal(err) =>
              logger.error(s"Error executing $runProc: $err")
              throw err
          }
        }

        val bytes = Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
        val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)
        Marshal(chunked).toResponseFor(ctxt.requestContext.request)
      }

      for {
        (runProc, uploads) <- uploadFutures
        resp <- marshalResponse(runProc, uploads)
      } yield {
        resp
      }
    }
  }

}