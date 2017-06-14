package jabroni.exec

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, Multipart}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.{JobId, nextJobId}
import jabroni.exec.model.{ProcessException, RunProcess, Upload}
import jabroni.exec.rest.MultipartExtractor
import jabroni.exec.run.ProcessRunner
import jabroni.rest.worker.WorkContext

import scala.concurrent.{Await, Future}
import scala.util.Failure
import scala.util.control.NonFatal

/**
  * Represents the worker logic for an execution as triggered from a match on the exchange
  *
  */
trait ExecutionHandler {

  def onExecute(ctxt: WorkContext[Multipart.FormData]): Unit
}

object ExecutionHandler {

  def apply(execConfig: ExecConfig) = new Instance(execConfig)

  class Instance(val execConfig: ExecConfig) extends ExecutionHandler with StrictLogging {

    import execConfig.serverImplicits._

    def asErrorResponse(exp: ProcessException) = {
      HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
    }

    override def onExecute(ctxt: WorkContext[Multipart.FormData]) = {

      val jobId: JobId = ctxt.matchDetails.map(_.jobId).getOrElse(nextJobId)
      val runner = execConfig.newRunner(ctxt, jobId)
      val handlerFuture = onRun(runner, ctxt, jobId).recover {
        case pr: ProcessException =>
          asErrorResponse(pr)
        case NonFatal(other) =>
          logger.error(s"translating error $other as a process exception")
          asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), ctxt.matchDetails, Nil))
      }
      ctxt.completeWith(handlerFuture)
    }


    def onRun(runner: ProcessRunner,
              ctxt: WorkContext[Multipart.FormData],
              jobId: JobId,
              // TODO - this should be determined from the content-type of the request, which we have
              outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {
      import ctxt.requestContext._
      import jabroni.rest.multipart.MultipartFormImplicits._

      def uploadDir = {
        execConfig.uploads.dir(jobId).getOrElse(sys.error("Invalid configuration - upload dir not set"))
      }
      val uploadFutures: Future[(RunProcess, List[Upload])] = MultipartExtractor(execConfig.uploads, ctxt.request, uploadDir, execConfig.chunkSize)

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