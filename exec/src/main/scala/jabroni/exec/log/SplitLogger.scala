package jabroni.exec.log

import java.io.{Closeable, Flushable}

import com.typesafe.scalalogging.LazyLogging

import scala.sys.process.ProcessLogger
import scala.util.control.NonFatal


object SplitLogger {
  def apply(first: ProcessLogger, theRest: ProcessLogger*) = {
    new SplitLogger(first :: theRest.toList)
  }
}

case class SplitLogger(loggers: List[ProcessLogger]) extends ProcessLogger with LazyLogging with AutoCloseable with Flushable {
  def add(pl: ProcessLogger) = copy(loggers = loggers :+ pl)

  override def out(s: => String): Unit = {
    lazy val string = s
    withLogger(_.out(string))
  }

  override def err(s: => String): Unit = {
    lazy val string = s
    withLogger(_.err(string))
  }

  private def withLogger(f: ProcessLogger => Unit): Unit = {
    loggers.foreach { pl =>
      try {
        f(pl)
      } catch {
        case NonFatal(e) =>
          logger.error(s"a processes logger threw $e", e)
      }
    }
  }

  override def buffer[T](f: => T): T = {
    val tea = f
    loggers.foreach(_.buffer(tea))
    tea
  }

  override def close(): Unit = {
    withLogger {
      case c: Closeable => c.close()
      case _ =>
    }
  }

  override def flush(): Unit = {
    withLogger {
      case f: Flushable => f.flush()
      case _ =>
    }
  }
}