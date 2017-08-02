package agora.exec.log

import java.util.concurrent.LinkedBlockingQueue

import com.typesafe.scalalogging.StrictLogging

import scala.collection.immutable.Stream
import scala.concurrent.{Future, Promise}
import scala.sys.process.ProcessLogger
import scala.util.{Failure, Try}

object StreamLogger {

  def apply() = new StreamLogger(handle(PartialFunction.empty))

  /**
    * @param outputStreamForExitCode a function which can provide an output stream based on a return value
    * @return a StreamLogger using the given partial function used to provide error output given an exit code
    */
  def forProcess(outputStreamForExitCode: PartialFunction[Try[Int], Stream[String]]): StreamLogger = {
    StreamLogger(handle(outputStreamForExitCode))
  }

  /**
    * @param outputStreamForExitCode a function which can provide an output stream based on a return value
    * @return a StreamLogger using the given partial function used to provide error output given an exit code
    */
  def handle(outputStreamForExitCode: PartialFunction[Try[Int], Stream[String]]): Try[Int] => Stream[String] = {
    outputStreamForExitCode.lift.andThen(_.getOrElse(Stream.empty[String]))
  }

}

/**
  * Made available from [[scala.sys.process.BasicIO]] ... a logger which exposes a blocking iterator which waits on output to become available
  *
  * @param exitCodeHandler a function on what to return for the given exit code
  */
case class StreamLogger(exitCodeHandler: Try[Int] => Stream[String]) extends ProcessLogger with AutoCloseable with StrictLogging {

  private val q = new LinkedBlockingQueue[Either[Try[Int], String]]

  private val exitCodePromise = Promise[Int]()

  // we append an empty one with 'next' so the call to 'iterator' doesn't block,
  // since the first call to 'next' is blocking!
  lazy val iterator = {
    Iterator.empty ++ (next().iterator)
  }

  private def next(): Stream[String] = {
    q.take match {
      case Left(code) => exitCodeHandler(code)
      case Right(s)   => Stream.cons(s, next())
    }
  }

  def complete(code: => Int): Future[Int] = complete(Try(code))

  def complete(err: Throwable): Future[Int] = complete(Failure(err))

  def complete(code: Try[Int]): Future[Int] = {
    if (exitCodePromise.tryComplete(code)) {
      logger.trace(s"Completing w/ $code")
      q.put(Left(code))
    }

    exitCode
  }

  def close() = complete(-10)

  def exitCode: Future[Int] = exitCodePromise.future

  private def append(s: => String) = {
    q.put(Right(s))
  }

  override def out(s: => String): Unit = append(s)

  override def err(s: => String): Unit = append(s)

  override def buffer[T](f: => T): T = f
}
