package agora.exec.run

import agora.api.exchange.SubmitJob
import agora.domain.IterableSubscriber
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.exchange.ExchangeClient
import akka.http.scaladsl.client.RequestBuilding
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.Try

/**
  * A client of the ExecutionRoutes
  *
  * @param exchange
  * @param defaultFrameLength
  * @param allowTruncation
  * @param requestWorkOnFailure
  * @param uploadTimeout
  */
case class RemoteRunner(exchange: ExchangeClient, defaultFrameLength: Int, allowTruncation: Boolean, requestWorkOnFailure: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  import exchange.execContext
  import exchange.materializer

  override def run(proc: RunProcess): ProcessOutput = {
    runAndSelect(proc).map(_._2)
  }

  @deprecated("delete", "me")
  def execute(proc: RunProcess): ProcessOutput = {

    val job = RemoteRunner.execAsJob(proc)

    exchange.enqueue(job).map { completedWork =>
      val resp = completedWork.onlyResponse
      val iter = IterableSubscriber.iterate(resp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
      proc.filterForErrors(iter)
    }
  }

  final def runAndSelect(cmd: String, theRest: String*): Future[(ExecutionClient, Iterator[String])] = {
    runAndSelect(RunProcess(cmd :: theRest.toList, Map[String, String]()))
  }

  /**
    * Executes the job via the exchange
    *
    * @param proc the job to execute
    * @return both the direct execution client and the job output
    */
  def runAndSelect(proc: RunProcess): Future[(ExecutionClient, Iterator[String])] = {
    val job = RemoteRunner.execAsJob(proc)

    val clientPromise = Promise[ExecutionClient]()
    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { workerClient =>
      val executionClient = ExecutionClient(workerClient.rest, defaultFrameLength, allowTruncation)
      clientPromise.tryComplete(Try(executionClient))
      executionClient.execute(proc)
    }

    for {
      executionClient <- clientPromise.future
      httpResponses   <- workerResponses
    } yield {
      val httpResp = httpResponses.onlyResponse
      val iter     = IterableSubscriber.iterate(httpResp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
      val output   = proc.filterForErrors(iter)
      (executionClient, output)
    }
  }

  override def close(): Unit = {
    exchange.close()
  }
}

object RemoteRunner extends RequestBuilding {

  /**
    * @see ExecutionHandler#newWorkspaceSubscription for the flip-side of this which prepares the work subscription
    * @param runProcess
    * @return
    */
  def execAsJob(runProcess: RunProcess): SubmitJob = {
    import agora.api.Implicits._
    runProcess.asJob.matching(ExecutionRoutes.execCriteria)
  }
}
