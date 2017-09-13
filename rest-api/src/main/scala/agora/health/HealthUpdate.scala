package agora.health

import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.worker.WorkerDetails
import agora.io.BaseActor
import akka.actor.{ActorRefFactory, ActorSystem, Cancellable, Props}

import scala.concurrent.duration.FiniteDuration

/**
  * The HealthUpdate periodically updates exchange subscriptions w/ health data so that:
  * 1) jobs can match/choose against health data
  * 2) the updates can serve as a heartbeat -- workers with state health data could be automatically cancelled
  */
object HealthUpdate {

  private object UpdateHealth

  class UpdateActor(exchange : Exchange) extends BaseActor {
    override def receive : Receive = {
      case UpdateHealth => {
        import io.circe.generic.auto._
        import io.circe.syntax._
        val health = HealthDto()
//        val subscriptino = WorkSubscription(WorkerDetails(Map("health" -> health).asJson))
//        exchange.subscribe()
      }
    }

  }

  def props(exchange : Exchange) = Props(new UpdateActor(exchange))

  def schedule(exchange : Exchange, frequency : FiniteDuration)(implicit sys : ActorSystem): Cancellable = {
    val healthUpdate = sys.actorOf(props(exchange), "healthUpdate")
    import sys.dispatcher
    sys.scheduler.schedule(frequency, frequency, healthUpdate, UpdateHealth)
  }
}