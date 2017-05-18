package jabroni.exec.log

import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import jabroni.domain.TryIterator
import jabroni.exec.{ProcessException, RunProcess}

import scala.sys.process.ProcessLogger
import scala.util.{Failure, Success, Try}


/**
  * Contains all the places where stuff will be logged
  */
class ProcessLoggers(jobName: String,
                     logDirOpt: Option[Path],
                     errorLimit: Option[Int],
                     proc: RunProcess) extends IterableLogger with LazyLogging {

  override def toString = {
    s"""ProcessLoggers for $proc {
       |     logDir             : $logDirOpt
       |    errorLimit         : $errorLimit
       |}
    """.stripMargin
  }

  private val stdErrLog = StreamLogger()

  private val limitedErrorLog = errorLimit.fold(stdErrLog: ProcessLogger) { limit => LimitLogger(limit, stdErrLog) }

  def onIterError(failure: Try[Int]) = {
    stdErrLog.complete(failure)
    val stdErr = stdErrLog.iterator.toList
    val json = ProcessException(proc, failure, stdErr).json.noSpaces
    Stream(proc.errorMarker, json)
  }

  private val stdOutLog = StreamLogger.forProcess(jobName) {
    case Success(n) if proc.successExitCodes.contains(n) => Stream.empty
    case failure => onIterError(failure)
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

  def add(pl: ProcessLogger) = {
    splitLogger = splitLogger.add(pl)
  }

  private var splitLogger: SplitLogger = {
    val all = SplitLogger(JustStdOut(stdOutLog), JustStdErr(limitedErrorLog))
    logDirOpt.fold(all) { logDir =>
      val errLog = ProcessLogger(logDir.resolve("std.err").toFile)
      val withStdErr = all.add(JustStdErr(errLog))

      val outLog = ProcessLogger(logDir.resolve("std.out").toFile)
      withStdErr.add(JustStdOut(outLog))
    }
  }

  override def out(s: => String): Unit = splitLogger.out(s)

  override def err(s: => String): Unit = splitLogger.err(s)

  override def buffer[T](f: => T): T = f

  override def complete(exception: Throwable): Unit = splitLogger.complete(exception)
}