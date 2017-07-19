package agora.exec

import agora.api.exchange.WorkSubscription
import agora.api.{JobId, nextJobId}
import agora.exec.model.{ProcessException, RunProcess}
import agora.exec.run.ProcessRunner
import agora.rest.worker.WorkContext
import akka.NotUsed
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.{`application/json`, `text/plain(UTF-8)`}
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, Future}
import scala.util.Failure
import scala.util.control.NonFatal

/**
  * Represents the worker logic for an execution as triggered from a match on the exchange
  *
  */
trait ExecutionHandler {

  def onExecute(ctxt: WorkContext[RunProcess]): Unit
}

object ExecutionHandler extends StrictLogging {

  /**
    * Sets up a subscription for execution jobs
    *
    * @param subscription
    * @return
    */
  def prepareSubscription(subscription: WorkSubscription) = {
    import agora.api.Implicits._
    subscription.matchingSubmission(("topic" === "exec").asMatcher)
  }

  def apply(execConfig: ExecConfig) = new Instance(execConfig)

  class Instance(val execConfig: ExecConfig) extends ExecutionHandler {

    import execConfig.serverImplicits._

    def asErrorResponse(exp: ProcessException) = {
      HttpResponse(status = InternalServerError, entity = HttpEntity(`application/json`, exp.json.noSpaces))
    }

    override def onExecute(ctxt: WorkContext[RunProcess]) = {

      val jobId: JobId = ctxt.matchDetails.map(_.jobId).getOrElse(nextJobId)
      val handlerFuture = onRun(ctxt, jobId).recover {
        case pr: ProcessException =>
          asErrorResponse(pr)
        case NonFatal(other) =>
          logger.error(s"translating error $other as a process exception")
          asErrorResponse(ProcessException(RunProcess(Nil), Failure(other), ctxt.matchDetails, Nil))
      }
      ctxt.completeWith(handlerFuture)
    }

    def onRun(ctxt: WorkContext[RunProcess],
              jobId: JobId,
              // TODO - this should be determined from the content-type of the request, which we have
              outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {
      import ctxt.requestContext._

      val runProc = ctxt.request
      val runner  = execConfig.newRunner(runProc, ctxt.matchDetails, jobId)

      def marshalResponse(runProc: RunProcess): Future[HttpResponse] = {
        val bytes                       = asByteIterator(runner, execConfig.uploadTimeout, runProc)
        val chunked: HttpEntity.Chunked = HttpEntity(outputContentType, bytes)
        Marshal(chunked).toResponseFor(ctxt.requestContext.request)
      }

      for {
        resp <- marshalResponse(runProc)
      } yield {
        resp
      }
    }
  }

  def asByteIterator(runner: ProcessRunner, timeout: FiniteDuration, runProc: RunProcess): Source[ByteString, NotUsed] = {
    def run = {
      try {
        Await.result(runner.run(runProc), timeout)
      } catch {
        case NonFatal(err) =>
          logger.error(s"Error executing $runProc: $err")
          throw err
      }
    }

    Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
  }

}
