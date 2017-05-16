package jabroni.exec.log

import scala.sys.process.ProcessLogger

case class JustStdOut(logger: ProcessLogger) extends DelegateLogger(logger) {
  override def out(s: => String): Unit = logger.out(s)

  override def err(s: => String): Unit = {}

  override def buffer[T](f: => T): T = logger.buffer(f)
}