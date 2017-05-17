package jabroni.exec.log

import java.io.{Closeable, Flushable}

import scala.sys.process.ProcessLogger


abstract class DelegateLogger(val logger: ProcessLogger) extends ProcessLogger with AutoCloseable with Flushable {
  override def out(s: => String): Unit = {}

  override def err(s: => String): Unit = logger.err(s)

  override def buffer[T](f: => T): T = logger.buffer(f)

  override def close(): Unit = logger match {
    case c: Closeable => c.close
    case _ =>
  }

  override def flush(): Unit = logger match {
    case f: Flushable => f.flush
    case _ =>
  }
}

object DelegateLogger {
  def unapply(dl : DelegateLogger) : Option[ProcessLogger] = Option(dl.logger)
}