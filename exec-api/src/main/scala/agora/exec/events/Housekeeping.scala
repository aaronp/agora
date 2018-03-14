package agora.exec.events

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import agora.io.BaseActor
import akka.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill, Props}

import scala.concurrent.duration.FiniteDuration

/**
  * A place to add housekeeping events for periodic execution
  */
trait Housekeeping extends Cancellable with AutoCloseable {

  /**
    * Add a callback to be invoked whenever the housekeeping is done
    *
    * @param f the callback to invoke when housekeeping is run
    */
  def registerHousekeepingEvent(f: Housekeeping.CleanupCallback): Unit

  override def close() = cancel()
}

object Housekeeping {
  type CleanupCallback = () => Unit

  private val counter = new AtomicInteger(0)

  case object Cleanup

  private case class Register(f: CleanupCallback)

  /**
    * Create a housekeeping actor to run its jobs every period after an optional initial delay
    *
    * @param period
    * @param initialDelay
    * @param system
    * @return
    */
  def every(period: FiniteDuration, initialDelay: FiniteDuration = null)(implicit system: ActorSystem): Housekeeping = {
    import system.dispatcher
    val actor = system.actorOf(Props(new CleanupActor), s"housekeeping-${counter.incrementAndGet()}")
    system.scheduler.schedule(Option(initialDelay).getOrElse(period), period, actor, Cleanup)
    new HouseKeepingClient(actor, s"Houskeeping run every ${period}")
  }

  private class HouseKeepingClient(houseKeepingActor: ActorRef, override val toString: String) extends Housekeeping {
    override def registerHousekeepingEvent(f: CleanupCallback): Unit = {
      houseKeepingActor ! Register(f)
    }

    private val cancelled = new AtomicBoolean(false)

    override def isCancelled(): Boolean = cancelled.get

    override def cancel() = {
      val ok = cancelled.compareAndSet(false, true)
      if (ok) {
        houseKeepingActor ! PoisonPill
      }
      ok
    }
  }

  private class CleanupActor() extends BaseActor {
    override def receive: Receive = handling(Nil)

    def handling(list: List[CleanupCallback]): Receive = {
      case Register(callback) => context.become(handling(callback :: list))
      case Cleanup =>
        context.system.eventStream.publish(Cleanup)
        list.par.foreach(_())
    }
  }

}
