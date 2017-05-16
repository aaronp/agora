package jabroni.exec

import java.io.{Closeable, Flushable}
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.LazyLogging

import scala.collection.immutable.Stream
import scala.concurrent.{Future, Promise}
import scala.sys.process.ProcessLogger
import scala.util.Try
import scala.util.control.NonFatal

object ProcessLoggers {


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

  /**
    * Made available from BasicIO
    *
    * @param nonzeroException
    */
  case class StreamLogger(nonzeroException: Boolean = true) extends ProcessLogger {

    private val q = new LinkedBlockingQueue[Either[Int, String]]

    private val exitCodePromise = Promise[Int]()
    lazy val iterator = {
      Iterator.empty ++ (next().iterator)
    }

    private def next(): Stream[String] = {
      q.take match {
        case Left(0) => Stream.empty
        case Left(code) => if (nonzeroException) scala.sys.error("Nonzero exit code: " + code) else Stream.empty
        case Right(s) => Stream.cons(s, next())
      }
    }

    def complete(code: => Int): Future[Int] = complete(Try(code))

    def complete(code: Try[Int]): Future[Int] = {
      q.put(Left(code.getOrElse(-1)))
      exitCodePromise.complete(code)
      exitCode
    }

    def exitCode = exitCodePromise.future

    private def append(s: => String) = q.put(Right(s))

    override def out(s: => String): Unit = append(s)

    override def err(s: => String): Unit = append(s)

    override def buffer[T](f: => T): T = f
  }


  case class LimitedLogger(limit: Int, logger: ProcessLogger) extends DelegateLogger(logger) {
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

  case class JustStdOut(logger: ProcessLogger) extends DelegateLogger(logger) {
    override def out(s: => String): Unit = logger.out(s)

    override def err(s: => String): Unit = {}

    override def buffer[T](f: => T): T = logger.buffer(f)
  }

  case class JustStdErr(logger: ProcessLogger) extends DelegateLogger(logger) {
    override def out(s: => String): Unit = {}

    override def err(s: => String): Unit = logger.err(s)

    override def buffer[T](f: => T): T = logger.buffer(f)
  }


  abstract class DelegateLogger(logger: ProcessLogger) extends ProcessLogger with AutoCloseable with Flushable {
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

}
