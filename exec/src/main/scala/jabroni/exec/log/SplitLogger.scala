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

case class SplitLogger(loggerList: List[ProcessLogger]) extends ProcessLogger with LazyLogging with AutoCloseable with Flushable {
  def add(pl: ProcessLogger) = copy(loggerList = pl :: loggerList)

  private lazy val loggers = loggerList.par

  override def out(s: => String): Unit = {
    lazy val string = s
    withLogger(_.out(string))
  }

  override def err(s: => String): Unit = {
    lazy val string = s
    withLogger(_.err(string))
  }

  private object Underlying {
    def unapply(pl: ProcessLogger): Option[ProcessLogger] = pl match {
      case DelegateLogger(other) => Underlying.unapply(other)
      case underlying => Option(underlying)
    }
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
    withLogger(_.buffer(tea))
    tea
  }

  def complete(code: Int) = {
    streamLoggers.foreach(_.complete(code))
  }

  def streamLoggers = loggerList.collect {
    case Underlying(x: StreamLogger) => x
  }

  override def close(): Unit = {
    flush()
    withLogger {
      case Underlying(c: Closeable) => c.close()
      case _ =>
    }
  }

  override def flush(): Unit = {
    withLogger {
      case Underlying(f: Flushable) => f.flush()
      case _ =>
    }
  }
}