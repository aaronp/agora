package jabroni.exec.log

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.immutable.Stream
import scala.concurrent.{Future, Promise}
import scala.sys.process.ProcessLogger
import scala.util.Try


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
