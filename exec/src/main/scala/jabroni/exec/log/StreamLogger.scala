package jabroni.exec.log

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.immutable.Stream
import scala.concurrent.{Future, Promise}
import scala.sys.process.ProcessLogger
import scala.util.Try

object StreamLogger {

  def apply(name: String = "process") = new StreamLogger(handle(name)(PartialFunction.empty))

  def forProcess(name: String)(pf: PartialFunction[Int, Stream[String]]): StreamLogger = {
    StreamLogger(handle(name)(pf))
  }

  def handle(name: String = "process")(pf: PartialFunction[Int, Stream[String]]): Int => Stream[String] = {
    pf.lift.andThen(_.getOrElse(Stream.empty[String]))
  }

}

/**
  * Made available from BasicIO
  *
  * @param exitCodeHandler a function on what to return for the given exit code
  */
case class StreamLogger(exitCodeHandler: Int => Stream[String]) extends ProcessLogger with AutoCloseable {

  private val q = new LinkedBlockingQueue[Either[Int, String]]

  private val exitCodePromise = Promise[Int]()

  // we append an empty one with 'next' so the call to 'iterator' doesn't block,
  // since the first call to 'next' is blocking!
  lazy val iterator = {
    Iterator.empty ++ (next().iterator)
  }

  private def next(): Stream[String] = {
    q.take match {
      case Left(code) => exitCodeHandler(code)
      case Right(s) => Stream.cons(s, next())
    }
  }

  def complete(code: => Int): Future[Int] = complete(Try(code))

  def complete(code: Try[Int]): Future[Int] = {
    q.put(Left(code.getOrElse(-1)))
    exitCodePromise.complete(code)
    exitCode
  }

  def close() = complete(-10)

  def exitCode = exitCodePromise.future

  private def append(s: => String) = {
    q.put(Right(s))
  }

  override def out(s: => String): Unit = append(s)

  override def err(s: => String): Unit = append(s)

  override def buffer[T](f: => T): T = f
}
