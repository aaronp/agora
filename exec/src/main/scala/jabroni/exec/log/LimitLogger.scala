package jabroni.exec.log

import java.util.concurrent.atomic.AtomicInteger

import scala.sys.process.ProcessLogger

case class LimitLogger(limit: Int, logger: ProcessLogger) extends DelegateLogger(logger) {
  private val count = new AtomicInteger(0)

  def canAppend = count.incrementAndGet() <= limit

  override def out(s: => String): Unit = if (canAppend) {
    logger.out(s)
  }

  override def err(s: => String): Unit = if (canAppend) {
    logger.err(s)
  }

  override def buffer[T](f: => T): T = f
}
