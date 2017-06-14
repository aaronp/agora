package agora.rest.client

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.StrictLogging
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration

/**
  *  given some crash info, either prune that crash info or throw an exception
  */
trait RetryStrategy extends (Crashes => Crashes)

object RetryStrategy {

  def instance(f: Crashes => Crashes): RetryStrategy = new RetryStrategy {
    override def apply(c: Crashes) = f(c)
  }

  /**
    * Produces a strategy which will retry indefinitely w/ the given delay in between retries
    * @param delay
    * @return
    */
  def throttle(delay: FiniteDuration) = new ThrottleStrategy(delay)

  /**
    * Exposes a DSL for creating an exception strategy
    *
    * @param n
    */
  case class tolerate(n: Int) {
    def failuresWithin(d: FiniteDuration) = new CountingStrategy(n, d)
  }

  class ThrottleStrategy(delay: FiniteDuration) extends RetryStrategy with StrictLogging {
    override def apply(crashes: Crashes): Crashes = {
      logger.error(crashes.exception.getMessage, crashes.exception)
      if (delay.toMillis > 0) {
        Thread.sleep(delay.toMillis)
      }
      Crashes(Nil)
    }
  }

  /**
    * If we get 'nTimes' failures within the 'in' window then propagate an exception
    * @param nTimes
    * @param in
    */
  class CountingStrategy(nTimes: Int, in: FiniteDuration) extends RetryStrategy {
    override def toString = s"RetryStrategy($nTimes in $in)"

    private def logger = LoggerFactory.getLogger(getClass)

    def filter(crashes: Crashes, now: LocalDateTime): Crashes = {
      val threshold = now.minusNanos(in.toNanos)
      val filtered  = crashes.removeBefore(threshold)
      if (filtered.size > nTimes) {
        logger.error(s"Propagating ${crashes.exception}")
        throw crashes.exception
      } else {
        filtered
      }
    }

    override def apply(crashes: Crashes): Crashes = filter(crashes, LocalDateTime.now)
  }
}
