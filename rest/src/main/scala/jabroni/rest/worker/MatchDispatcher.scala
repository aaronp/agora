package jabroni.rest.worker

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.exchange.Exchange
import jabroni.api.worker.{DispatchWork, WorkerDetails}

/**
  * Sends work to the workers at 'details'
  */
class MatchDispatcher(implicit sys : ActorSystem, mat: Materializer) extends Exchange.OnMatch[Unit] with StrictLogging {

  private var clientForDetails = Map[WorkerDetails, WorkerClient]()

  def apply(jobMatch: Exchange.Match): Unit = {
    val (job, workers) = jobMatch
    workers.foreach {
      case (key, subscription, remaining) =>
        val workerOpt = clientForDetails.get(subscription.details) match {
          case Some(cached) => Option(cached)
          case None =>
            WorkerClient(subscription.details) match {
              case Left(err) =>
                onBadWorker(subscription.details, err)
                None
              case Right(client) =>
                clientForDetails = clientForDetails.updated(subscription.details, client)
                Option(client)
            }
        }

        workerOpt.foreach(_.dispatch(DispatchWork(key, job, remaining)))
    }
  }

  def onBadWorker(details: WorkerDetails, err: Throwable) = {
    logger.error(s"Couldn't dispatch to ${details} as 'path' isn't set: $err")
  }
}
