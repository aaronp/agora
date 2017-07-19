package agora.exec.run

import java.nio.file.Path

import agora.exec.log._
import agora.exec.model.RunProcess
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessBuilder}
import scala.util.{Failure, Success, Try}

/**
  * Something which can run commands
  */
case class LocalRunner(workDir: Option[Path] = None, mkLogger: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit ec: ExecutionContext)
    extends ProcessRunner
    with StrictLogging {

  def withLogger(newLogger: RunProcess => IterableLogger) = copy(mkLogger = newLogger)
  override def run(preparedProcess: RunProcess): Future[Iterator[String]] = {
    val builder: ProcessBuilder = Process(preparedProcess.command, workDir.map(_.toFile), preparedProcess.env.toSeq: _*)
    val logger                  = execute(builder, preparedProcess)
    Future.successful(logger.iterator)
  }

  def execute(builder: ProcessBuilder, proc: RunProcess) = {

    val iterableLogger: IterableLogger = mkLogger(proc)
    val future: Future[Int] = {
      val startedTry: Try[Process] = Try {
        builder.run(iterableLogger)
      }
      startedTry match {
        case Success(process) => Future(process.exitValue())
        case Failure(err)     => Future.failed(err)
      }
    }

    future.onComplete {
      case Success(code) =>
        iterableLogger.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        iterableLogger.complete(err)
    }

    iterableLogger
  }

}
