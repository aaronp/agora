package miniraft.state

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.actor.{Actor, ActorRef, ActorRefFactory, PoisonPill, Props}
import miniraft.state.RaftTimer.timeouts
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.{Random, Try}

trait RaftTimer extends AutoCloseable {
  override def close() = {}

  /** @return debug info regarding the time state
    */
  def status: Future[String]

  /**
    * Cancel's the current timeout
    *
    * @return true if the call had effect
    */
  def cancel(): Future[Boolean]

  /** resets the timeout
    *
    * The implementation should provide sensible default timeouts,
    * but the caller can opt to provide a delay. For example,
    * in the occasion of an endpoint failure or a user interaction,
    * we may want to schedule at a sooner (or event immediate) time
    *
    * In the event of a timeout, the call-back can elect to
    * schedule another timeout event at the given delay.
    *
    * @param delay the delay to use when set, otherwise None
    * @return true if a scheduled timeout has been cancelled by invoking, false if either it couldn't be cancelled or
    *         a task wasn't scheduled in the first place
    */
  def reset(delay: Option[FiniteDuration] = None): Future[Boolean]
}

/**
  * Timers are tricky, as they need to be given to a [[ClusterProtocol]], but the cluster protocol
  * needs to reference nodes, which the timers in turn need to have a reference to.
  *
  * Because of this circular reference, we make an 'initializable' timer, which is a valid RaftTimer,
  * but resets won't take affect until it has been initialized.
  *
  */
trait InitialisableTimer extends RaftTimer {

  /** @param onTimeout the callback to use for this timer
    * @return true (eventually) if the timer had not yet been initialized, false otherwise
    */
  def initialise(onTimeout: RaftTimer => Unit): Future[Boolean]
}

object InitialisableTimer {

  def initialise(timer: RaftTimer)(onTimeout: RaftTimer => Unit): Future[Boolean] = {
    timer match {
      case it: InitialisableTimer => it.initialise(onTimeout)
      case other                  => Future.failed(new Exception(s"$other is not an initialisable timer"))
    }
  }

  def apply(label: String, min: FiniteDuration, max: FiniteDuration)(implicit factory: ActorRefFactory): InitialisableTimer = {
    apply(label, timeouts(min, max))
  }

  def apply(label: String, nextTimeout: Iterator[FiniteDuration])(implicit factory: ActorRefFactory): InitialisableTimer = {
    forActor(label, factory.actorOf(props(label, nextTimeout)))
  }

  private def forActor(label: String, actorBasedTimer: ActorRef): InitialisableTimer = {
    new ActorBasedTimer(label, actorBasedTimer)
  }

  /**
    * Timers are tricky, as they need to be given to a [[ClusterProtocol]], but the cluster protocol
    * needs to reference nodes, which the timers in turn need to have a reference to.
    *
    * Because of this circular reference, we make an 'initializable' timer
    *
    * @param name
    * @param timerActor
    */
  class ActorBasedTimer(name: String, timerActor: ActorRef) extends InitialisableTimer {
    override def toString = s"$name unstarted timer"

    /** @param onTimeout the callback to use for the timer, if it has not yet been set
      * @return a future of true if the callback has been set, false if the timer has already been initialised
      */
    override def initialise(onTimeout: RaftTimer => Unit): Future[Boolean] = {
      val promise = Promise[Boolean]()
      timerActor ! InitializeTimer(onTimeout, promise)
      promise.future
    }

    def status = {
      val promise = Promise[String]()
      timerActor ! StatusRequest(promise)
      promise.future
    }

    override def cancel() = {
      val cancelled = Promise[Boolean]()
      timerActor ! CancelTimer(cancelled)
      cancelled.future
    }

    override def reset(delay: Option[FiniteDuration]) = {
      val isReset = Promise[Boolean]()
      timerActor ! ResetTimer(delay, isReset)
      isReset.future
    }
    override def close() = {
      cancel()
      timerActor ! PoisonPill
    }
  }

  private sealed trait TimerMessage

  private case class CancelTimer(result: Promise[Boolean]) extends TimerMessage

  private case class StatusRequest(result: Promise[String]) extends TimerMessage

  private case object OnTimeout extends TimerMessage

  private case class ResetTimer(delay: Option[FiniteDuration], result: Promise[Boolean]) extends TimerMessage

  private case class InitializeTimer(onTimeout: RaftTimer => Unit, promise: Promise[Boolean]) extends TimerMessage

  def props(label: String, nextTimeout: Iterator[FiniteDuration]) = {
    Props(new TimerActor(label, nextTimeout))
  }

  private class TimerActor(label: String, nextTimeout: Iterator[FiniteDuration]) extends Actor {
    type TimerReceive = (TimerMessage => Unit)

    override def unhandled(message: Any): Unit = {
      super.unhandled(message)
      sys.error(s"$self couldn't handle $message")
    }

    override def receive: Receive = unstarted(None)

    lazy val asRaftTimer = forActor(label, self)

    def unstarted(reset: Option[ResetTimer]): Receive = wrap("unstarted") {
      case InitializeTimer(handler, promise) =>
        promise.success(true)
        context.become(idle(handler))
        reset.foreach { msg =>
          self ! msg
        }
      case OnTimeout =>
        logger.info(s"Ignoring timeout on unstarted $label timer")
      case StatusRequest(promise)           => promise.trySuccess(s"$label unstarted")
      case CancelTimer(promise)             => promise.trySuccess(false)
      case msg: ResetTimer if reset.isEmpty => context.become(unstarted(Option(msg)))
      case msg: ResetTimer =>
        reset.foreach {
          case ResetTimer(_, promise) => promise.trySuccess(false)
        }
        context.become(unstarted(Option(msg)))
    }

    def reset(delay: Option[FiniteDuration], onTimeout: RaftTimer => Unit) = {
      val nextDelay = delay.getOrElse(nextTimeout.next())
      import context.dispatcher
      if (nextDelay.toMillis == 0) {
        onTimeout(asRaftTimer)
      } else {
        val cancel = context.system.scheduler.scheduleOnce(nextDelay, self, OnTimeout)
        val due    = ZonedDateTime.now.plusNanos(nextDelay.toNanos)
        context.become(awaitingTimeout(cancel, due, onTimeout))
      }
    }

    private val logger = LoggerFactory.getLogger(getClass)

    def wrap(prefix: String)(handler: TimerReceive): Receive = {
      if (logger.isTraceEnabled) {
        case msg: TimerMessage =>
          logger.trace(s"$prefix : on($msg)")
          handler(msg)
      } else {
        case msg: TimerMessage => handler(msg)
      }
    }

    def idle(onTimeout: RaftTimer => Unit): Receive = wrap(s"$label idle") {
      case StatusRequest(promise)      => promise.trySuccess(s"$label idle")
      case InitializeTimer(_, promise) => promise.success(false)
      case CancelTimer(promise) =>
        promise.trySuccess(false)
      case OnTimeout =>
        logger.debug(s"${label} ignoring timeout as we're idle")
      case ResetTimer(delay, promise) =>
        reset(delay, onTimeout)
        promise.trySuccess(false)
    }

    def awaitingTimeout(cancellable: akka.actor.Cancellable, due: ZonedDateTime, onTimeout: RaftTimer => Unit): Receive =
      wrap(s"$label awaiting timeout due at ${DateTimeFormatter.ISO_TIME.format(due)}") {
        case StatusRequest(promise) => promise.trySuccess(s"$label awaiting timeout due at ${DateTimeFormatter.ISO_TIME.format(due)}")
        case OnTimeout =>
          logger.debug(s"${label} timed out")
          onTimeout(asRaftTimer)
        case CancelTimer(promise) =>
          promise.tryComplete(Try(cancellable.cancel()))
          context.become(idle(onTimeout))
        case InitializeTimer(_, promise) => promise.success(false)
        case ResetTimer(delay, promise) =>
          reset(delay, onTimeout)
          promise.tryComplete(Try(cancellable.cancel()))
      }
  }

}

object RaftTimer {

  /** @return a stream of timeouts to represent a random range
    */
  def timeouts(min: FiniteDuration, max: FiniteDuration, rnd: Random = new java.util.Random()): Iterator[FiniteDuration] = {
    require(min <= max, s"invalid range $min - $max")
    import concurrent.duration._
    val range = (max.toMillis - min.toMillis).toInt
    Iterator.continually(rnd).map { r =>
      min + r.nextInt(range).millis
    }
  }
}
