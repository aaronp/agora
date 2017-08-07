package agora.exec.run

import agora.api.SubscriptionKey
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

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
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

  import exchange.{execContext, materializer}

  override def run(proc: RunProcess): ProcessOutput = {
    runAndSelect(proc).map(_._2)
  }

  final def runAndSelect(cmd: String, theRest: String*): Future[(ExecutionClient, Iterator[String])] = {
    runAndSelect(RunProcess(cmd :: theRest.toList, Map[String, String]()))
  }

  /**
    * Executes the job via the exchange, returning an [[ExecutionClient]] which can be used to upload/execute
    * jobs to the worker which produced the output.
    *
    * It's called 'runAndSelect' because it can be used to select a worker which can then be used to ensure
    * the same workspace is used on the same server.
    *
    * @param proc the job to execute
    * @return both the direct execution client and the job output
    */
  def runAndSelect(proc: RunProcess): Future[(ExecutionClient, Iterator[String])] = {
    val job = RemoteRunner.execAsJob(proc)

    // TODO - don't just reuse the direct client in the ExecutionClient, but rather continue to go via
    // the exchange to ensure we don't overload our worker
    val clientPromise = Promise[SubscriptionKey]()
    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { workerClient =>
      val executionClient = ExecutionClient(workerClient.rest, defaultFrameLength, allowTruncation)
      clientPromise.tryComplete(Try(workerClient.matchDetails.subscriptionKey))
      executionClient.execute(proc)
    }

    for {
      executionClient: ExecutionClient <- clientPromise.future
      httpResponses                    <- workerResponses
    } yield {
      val httpResp = httpResponses.onlyResponse
      val iter     = IterableSubscriber.iterate(httpResp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
      val output   = proc.filterForErrors(iter)
      (executionClient, output)
    }
  }
  def runAndSelectDirect(proc: RunProcess): Future[(ExecutionClient, Iterator[String])] = {
    val job = RemoteRunner.execAsJob(proc)

    // TODO - don't just reuse the direct client in the ExecutionClient, but rather continue to go via
    // the exchange to ensure we don't overload our worker
    val clientPromise = Promise[ExecutionClient]()
    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { workerClient =>
      val executionClient = ExecutionClient(workerClient.rest, defaultFrameLength, allowTruncation)
      clientPromise.tryComplete(Try(executionClient))
      val future = executionClient.execute(proc)

      future.onComplete {
        case result =>
          exchange.take(workerClient.matchDetails.subscriptionKey, 1)
      }
      future
    }

    for {
      executionClient: ExecutionClient <- clientPromise.future
      httpResponses                    <- workerResponses
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
