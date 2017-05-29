package miniraft.state

import java.util.concurrent._

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration
import scala.util.Random


trait RaftTimer {

  /**
    * Cancel's the current timeout
    *
    * @return true if the call had effect
    */
  def cancel(): Boolean

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
    */
  def reset(delay: Option[FiniteDuration] = None): Boolean
}

object RaftTimer {

  private lazy val defaultSchedulerService = Executors.newScheduledThreadPool(2, new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread()
      t.setName("raft timer")
      t.setDaemon(true)
      t
    }
  })

  /**
    * Creates a timer which produces timeouts in the given range
    *
    * @param min       the min timeout value
    * @param max       the max timeout value
    * @param label     a label for logging
    * @param onTimeout the call-baack
    * @return a raft timer
    */
  def apply(min: FiniteDuration, max: FiniteDuration, label: String)(onTimeout: RaftTimer => Unit): RaftTimer = {
    apply(label, timeouts(min, max))(onTimeout)
  }

  def apply(label: String, nextTimeout: Iterator[FiniteDuration], scheduler: ScheduledExecutorService = defaultSchedulerService)(onTimeout: RaftTimer => Unit): RaftTimer = {
    new Default(label, scheduler, nextTimeout)(onTimeout)
  }

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

  /**
    * Represents a [[ScheduledExecutorService]] backed RaftTimer
    *
    * @param label       used for logging purposes on timeouts
    * @param scheduler   the scheduler used to schedule (and cancel) timeouts
    * @param nextTimeout an infinite iterator of timeouts, used to represent our 'random' timer
    * @param onTimeout   what to do on timeout
    */
  class Default(label: String, scheduler: ScheduledExecutorService, nextTimeout: Iterator[FiniteDuration])(onTimeout: RaftTimer => Unit) extends RaftTimer
    with StrictLogging { self =>

    private object OnTimeout extends Runnable {
      override def run(): Unit = {
        logger.debug(s"$label invoking timeout...")
        Lock.synchronized {
          task = None
        }
        onTimeout(self)
      }
    }

    private def reschedule(timeout: FiniteDuration): Option[ScheduledFuture[_]] = {
      logger.debug(s"Scheduling timeout for $label after $timeout")
      timeout.toMillis match {
        case 0 =>
          OnTimeout.run
          None
        case n => Option(scheduler.schedule(OnTimeout, n, TimeUnit.MILLISECONDS))
      }
    }

    private var task: Option[ScheduledFuture[_]] = None

    private object Lock

    override def cancel() = Lock.synchronized {
      val cancelled = task.map(_.cancel(false)).getOrElse(false)
      task = None
      cancelled
    }

    override def reset(delay: Option[FiniteDuration] = None): Boolean = Lock.synchronized {
      val worked = task match {
        case None => true
        case Some(cancellable) => cancellable.cancel(false)
      }
      val timeout = delay.getOrElse(nextTimeout.next())
      task = reschedule(timeout)
      worked
    }
  }

}