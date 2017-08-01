package agora.exec.log

import agora.api.`match`.MatchDetails
import agora.domain.TryIterator
import agora.exec.model.{ProcessException, RunProcess}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.sys.process.ProcessLogger
import scala.util.{Failure, Success, Try}

/**
  * Contains all the places where stuff will be logged.
  *
  * At a minimum it adds loggers, and can add/remote process loggers, as well as be 'completed' by the process
  */
class ProcessLoggers(val proc: RunProcess, override val matchDetails: Option[MatchDetails], val errorLimit: Option[Int] = None) extends IterableLogger with LazyLogging {

  override def toString = s"""ProcessLoggers($matchDetails,\n$proc,\n$errorLimit)""".stripMargin

  private val stdErrLog = StreamLogger()
  private val limitedErrorLog = errorLimit.fold(stdErrLog: ProcessLogger) { limit =>
    LimitLogger(limit, stdErrLog)
  }

  private object Lock

  private val stdOutLog = StreamLogger.forProcess {
    case Success(n) if proc.successExitCodes.contains(n) => Stream.empty
    case failure => onIterError(failure)
  }

  private var splitLogger: SplitLogger = SplitLogger(JustStdOut(stdOutLog), JustStdErr(limitedErrorLog))

  override def exitCodeFuture: Future[Int] = stdOutLog.exitCode

  def stdErr = stdErrLog.iterator.toList

  private def onIterError(failure: Try[Int]) = {
    stdErrLog.complete(failure)
    val json = ProcessException(proc, failure, None, stdErr).json.spaces2.lines.toStream
    proc.errorMarker #:: json
  }

  def processLogger: ProcessLogger = splitLogger

  def complete(code: => Int) = splitLogger.complete(code)

  def iterator: Iterator[String] = TryIterator(stdOutLog.iterator) {
    case err: ProcessException => throw err
    case err =>
      complete(err)
      val res = splitLogger.completedResult.getOrElse(Failure(new Throwable("process hasn't completed")))
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
//    val processExp = exception match {
//      case pe: ProcessException => pe
//      case other => ProcessException(proc, Failure(other), None, stdErr)
//    }
    splitLogger.complete(exception)
  }
}
