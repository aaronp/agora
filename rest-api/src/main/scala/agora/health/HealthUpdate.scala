package agora.health

import agora.api.exchange.{Exchange, UpdateSubscriptionAck}
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import akka.actor.{ActorSystem, Cancellable}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * The HealthUpdate periodically updates exchange subscriptions w/ health data so that:
  * 1) jobs can match/choose against health data
  * 2) the updates can serve as a heartbeat -- workers with state health data could be automatically cancelled
  */
object HealthUpdate extends StrictLogging {

  /**
    * Schedules periodic updates of the given subscription, appending 'health' information based on the [[HealthDto]]
    * @param exchange
    * @param key
    * @param frequency
    * @param sys
    * @return
    */
  def schedule(exchange: Exchange, key: SubscriptionKey, frequency: FiniteDuration)(implicit sys: ActorSystem): Cancellable = {
    import sys.dispatcher
    sys.scheduler.schedule(frequency, frequency) {
      updateHealth(exchange, key)
    }
  }

  def updateHealth(exchange: Exchange, key: SubscriptionKey, health: HealthDto = HealthDto())(implicit ec: ExecutionContext): Future[UpdateSubscriptionAck] = {

    val future: Future[UpdateSubscriptionAck] = exchange.updateSubscriptionDetails(key, WorkerDetails.empty.append("health", health))
    future.onComplete {
      case Success(UpdateSubscriptionAck(id, Some(before), Some(after))) => logger.trace(s"Updated health data for $id from $before to $after")
      case Success(UpdateSubscriptionAck(id, _, _)) =>
        logger.error(s"Couldn't update health data for unknown subscription $id")
      case Failure(err) => logger.error(s"Health updated failed w/ $err")
    }
    future
  }
}
