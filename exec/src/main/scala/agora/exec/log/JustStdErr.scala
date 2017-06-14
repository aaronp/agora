package agora.exec.log

import scala.sys.process.ProcessLogger

case class JustStdErr(override val logger: ProcessLogger) extends DelegateLogger(logger) {
  override def out(s: => String): Unit = {}

  override def err(s: => String): Unit = logger.err(s)

  override def buffer[T](f: => T): T = logger.buffer(f)
}
