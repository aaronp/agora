package agora.exec.log

import agora.api.`match`.MatchDetails
import agora.exec.model.{FileResult, ProcessException, RunProcess}
import agora.io.TryIterator
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger
import scala.util.{Failure, Success, Try}

/**
  * Contains all the places where stuff will be logged.
  *
  * At a minimum it adds loggers, and can add/remote process loggers, as well as be 'completed' by the process
  */
class ProcessLoggers(val proc: RunProcess, override val matchDetails: Option[MatchDetails]) extends IterableLogger with LazyLogging {

  private def errorLimit = proc.output.streaming.flatMap(_.errorLimit)

  private val stdErrLog = StreamLogger()
  private val limitedErrorLog = errorLimit.fold(stdErrLog: ProcessLogger) { limit =>
    LimitLogger(limit, stdErrLog)
  }

  private object Lock

  private val stdOutLog: StreamLogger = StreamLogger.forProcess {
    case resultFuture @ Success(n) =>
      proc.output.streaming match {
        case Some(streamingSettings) if !streamingSettings.isSuccessfulExitCode(n) => onIterError(resultFuture)
        case _                                                                     => Stream.empty
      }
    case failure => onIterError(failure)
  }

  private var splitLogger: SplitLogger = SplitLogger(JustStdOut(stdOutLog), JustStdErr(limitedErrorLog))

  override def exitCodeFuture: Future[Int] = stdOutLog.exitCode

  def fileResultFuture(implicit executionContext: ExecutionContext) = exitCodeFuture.map { exitCode =>
    FileResult(exitCode, proc.workspace, proc.output.stdOutFileName, proc.output.stdErrFileName, matchDetails)
  }

  def stdErr = stdErrLog.iterator.toList

  private def onIterError(failure: Try[Int]) = {
    stdErrLog.complete(failure)
    proc.output.streaming match {
      case Some(streamingSettings) =>
        val json = ProcessException(proc, failure, None, stdErr).json.spaces2.lines.toStream
        streamingSettings.errorMarker #:: json
      case _ => Stream.empty
    }
  }

  def processLogger: ProcessLogger = splitLogger

  def complete(code: => Int) = splitLogger.complete(code)

  def iterator: Iterator[String] = TryIterator(stdOutLog.iterator) {
    case err: ProcessException => throw err
    case err =>
      complete(err)
      val res = splitLogger.completedResult.getOrElse(Failure(new Exception("process hasn't completed")))
      throw ProcessException(proc, res, None, stdErrLog.iterator.toList)
  }

  override def add(pl: ProcessLogger): ProcessLoggers = Lock.synchronized {
    splitLogger = splitLogger.add(pl)
    this
  }

  override def out(s: => String): Unit = splitLogger.out(s)

  override def err(s: => String): Unit = splitLogger.err(s)

  override def buffer[T](f: => T): T = f

  override def complete(exception: Throwable): Unit = {
    splitLogger.complete(exception)
  }

  override def toString = s"""ProcessLoggers($matchDetails,\n$proc,\n$errorLimit)""".stripMargin

}
