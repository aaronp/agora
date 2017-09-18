package agora.exec.client

import agora.api.SubscriptionKey
import agora.api.exchange.SubmitJob
import agora.api.json.JMatcher
import agora.exec.model.{FileResult, RunProcess, RunProcessResult}
import agora.rest.exchange.ExchangeClient
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.java8.time._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.language.{implicitConversions, reflectiveCalls}
import scala.util.Try

/**
  * A client of the ExecutionRoutes
  *
  * @param exchange
  * @param defaultFrameLength
  * @param requestWorkOnFailure
  * @param uploadTimeout
  */
case class RemoteRunner(exchange: ExchangeClient, defaultFrameLength: Int, requestWorkOnFailure: Boolean, keyOpt: Option[SubscriptionKey] = None)(
    implicit uploadTimeout: FiniteDuration)
    extends ProcessRunner
    with AutoCloseable
    with FailFastCirceSupport
    with LazyLogging {

  import exchange.{execContext, materializer}

  override def run(input: RunProcess) = {
    input.output.stream match {
      case None    => runAndSave(input)
      case Some(_) => runAndSelect(input).flatMap(_.result)
    }
  }

  private def runAndSave(proc: RunProcess): Future[FileResult] = {
    val job = RemoteRunner.execAsJob(proc, keyOpt)
    exchange.enqueueAs[FileResult](job)
  }

  def withSubscription(key: SubscriptionKey): RemoteRunner = copy(keyOpt = Option(key))

  final def runAndSelect(cmd: String, theRest: String*): Future[SelectionOutput] = {
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
    * @return both the subscription key client and the job output
    */
  def runAndSelect(proc: RunProcess): Future[SelectionOutput] = {

    val job = RemoteRunner.execAsJob(proc, keyOpt)

    // TODO - don't just reuse the direct client in the ExecutionClient, but rather continue to go via
    // the exchange to ensure we don't overload our worker
    val subscriptionPromise = Promise[SelectionOutput]()
    val (_, workerResponses) = exchange.enqueueAndDispatch(job) { workerClient =>
      val key = workerClient.matchDetails.subscriptionKey

      //
      // Execute directly using an ExecutionClient
      //
      val executionClient: ExecutionClient = ExecutionClient(workerClient.rest, defaultFrameLength)
      val newRunner                        = withSubscription(key)
      val selection                        = SelectionOutput(key, workerClient.workerDetails.location, newRunner, executionClient, null)
      subscriptionPromise.tryComplete(Try(selection))
      executionClient.execute(proc)
    }

    for {
      selection     <- subscriptionPromise.future
      httpResponses <- workerResponses

      // NOTE: we don't strictly have to flatMap on this ack, but doing will propagate ack future failures, which we want
      takeAck <- exchange.take(selection.subscription, 1)
    } yield {
      require(takeAck.id == selection.subscription)
      logger.debug(s"Took another work item for ${selection.subscription}: $takeAck")

      val httpResp = httpResponses.onlyResponse

      val resultFuture: Future[RunProcessResult] = proc.output.stream match {
        case Some(streamingSettings) =>
          val result = streamingSettings.asResult(httpResp, defaultFrameLength)
          Future.successful(result)
        case None => Unmarshal(httpResp).to[FileResult]
      }
      selection.copy(result = resultFuture)
    }
  }

  override def close(): Unit = {
    exchange.close()
  }
}

object RemoteRunner extends RequestBuilding {

  import agora.api.Implicits._

  val runProcessCriteria: JMatcher = ("topic" === "execute").asMatcher

  /**
    * @see ExecutionHandler#newWorkspaceSubscription for the flip-side of this which prepares the work subscription
    * @param runProcess
    * @return
    */
  def execAsJob(runProcess: RunProcess, subscriptionOpt: Option[SubscriptionKey]): SubmitJob = {
    import agora.api.Implicits._
    val criteria = subscriptionOpt.fold(runProcessCriteria) { key =>
      runProcessCriteria.and("id" === key)
    }
    runProcess.asJob.matching(criteria)
  }
}
