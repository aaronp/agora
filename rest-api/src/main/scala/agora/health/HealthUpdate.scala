package agora.health

import agora.api.exchange.{Exchange, UpdateSubscriptionAck}
import agora.api.health.HealthDto
import agora.api.worker.SubscriptionKey
import agora.io.BaseActor
import akka.actor.{ActorSystem, Cancellable, Props}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

/**
  * The HealthUpdate periodically updates exchange subscriptions w/ health data so that:
  * 1) jobs can match/choose against health data
  * 2) the updates can serve as a heartbeat -- workers with state health data could be automatically cancelled
  */
object HealthUpdate extends StrictLogging {

  private object UpdateMsg

  private[health] case class RemoveKey(invalidKey: SubscriptionKey)

  private[health] object RemoveKey {
    def forAck(ack: UpdateSubscriptionAck): Option[RemoveKey] = {
      ack match {
        case UpdateSubscriptionAck(id, Some(before), Some(after)) =>
          logger.trace(s"Updated health data for $id from $before to $after")
          None
        case UpdateSubscriptionAck(id, _, _) =>
          logger.error(s"Couldn't update health data for unknown subscription $id")
          Option(RemoveKey(id))
      }
    }
  }

  private class UpdateActor(exchange: Exchange, initialKeys: Set[SubscriptionKey]) extends BaseActor {
    import context.dispatcher

    override def receive: Receive = updateHandler(initialKeys)

    def updateHandler(keys: Set[SubscriptionKey]): Receive = {
      case RemoveKey(key) =>
        val remaining = keys - key
        if (remaining.isEmpty) {
          logger.warn(s"All keys $initialKeys failed, stopping health update")
          context.stop(self)
        } else {
          logger.info(s"stopping health update for $key")
          context.become(updateHandler(remaining))
        }
      case UpdateMsg =>
        keys.foreach { key =>
          HealthDto.updateHealth(exchange, key).map(RemoveKey.forAck).onComplete {
            case Success(None) => // the success case
            case Success(Some(msg: RemoveKey)) =>
              self ! msg
            case Failure(err) =>
              logger.error(s"Update subscription for $key threw $err", err)
              self ! RemoveKey(key)
          }

        }
    }
  }

  /**
    * Schedules periodic updates of the given subscription, appending 'health' information based on the [[HealthDto]]
    *
    * @param exchange
    * @param keys
    * @param frequency
    * @param sys
    * @return
    */
  def schedule(exchange: Exchange, keys: Set[SubscriptionKey], frequency: FiniteDuration)(implicit sys: ActorSystem): Cancellable = {
    val actor = sys.actorOf(props(exchange, keys), "updateActor")
    import sys.dispatcher
    sys.scheduler.schedule(frequency, frequency, actor, UpdateMsg)
  }

  def props(exchange: Exchange, keys: Set[SubscriptionKey]) = Props(new UpdateActor(exchange, keys))

}
