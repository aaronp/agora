package jabroni.exec.log

import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import jabroni.domain.TryIterator
import jabroni.exec.{ProcessException, RunProcess}

import scala.sys.process.ProcessLogger
import scala.util.{Failure, Success, Try}


/**
  * Contains all the places where stuff will be logged.
  *
  * At a minimum it adds loggers
  */
class ProcessLoggers(jobName: String,
                     proc: RunProcess,
                     errorLimit: Option[Int] = None) extends IterableLogger with LazyLogging {

  override def toString = s"""$jobName ProcessLogger""".stripMargin

  private val stdErrLog = StreamLogger()
  private val limitedErrorLog = errorLimit.fold(stdErrLog: ProcessLogger) { limit => LimitLogger(limit, stdErrLog) }
  private object Lock
  private val stdOutLog = StreamLogger.forProcess(jobName) {
    case Success(n) if proc.successExitCodes.contains(n) => Stream.empty
    case failure => onIterError(failure)
  }

  private var splitLogger: SplitLogger = SplitLogger(JustStdOut(stdOutLog), JustStdErr(limitedErrorLog))

  private def onIterError(failure: Try[Int]) = {
    stdErrLog.complete(failure)
    val stdErr = stdErrLog.iterator.toList
    val json = ProcessException(proc, failure, stdErr).json.spaces2.lines.toStream
    proc.errorMarker #:: json
  }
  def processLogger: ProcessLogger = splitLogger

  def complete(code: => Int) = splitLogger.complete(code)

  def iterator: Iterator[String] = TryIterator(stdOutLog.iterator) {
    case err: ProcessException => throw err
    case err =>
      complete(err)
      val res = splitLogger.completedResult.getOrElse(Failure(new Throwable("process hasn't completed")))
      throw ProcessException(proc, res, stdErrLog.iterator.toList)
  }

  def add(pl: ProcessLogger) = Lock.synchronized {
    splitLogger = splitLogger.add(pl)
    this
  }

  def addUnderDir(logDir: Path): ProcessLoggers = {
    import jabroni.domain.io.implicits._
    addStdOut(ProcessLogger(logDir.resolve("std.out").toFile)).
    addStdErr(ProcessLogger(logDir.resolve("std.err").toFile))
  }

  def addStdOut(pl: ProcessLogger): ProcessLoggers = add(JustStdOut(pl))

  def addStdErr(pl: ProcessLogger): ProcessLoggers = add(JustStdErr(pl))

  override def out(s: => String): Unit = splitLogger.out(s)

  override def err(s: => String): Unit = splitLogger.err(s)

  override def buffer[T](f: => T): T = f

  override def complete(exception: Throwable): Unit = splitLogger.complete(exception)
}