package agora.exec.client

import java.nio.file.Path

import agora.exec.log._
import agora.exec.model._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessBuilder}
import scala.util.{Failure, Success, Try}

/**
  * Something which can execute [[RunProcess]]
  */
case class LocalRunner(val workDir: Option[Path] = None)(implicit ec: ExecutionContext) extends ProcessRunner with StrictLogging {

  override def toString = s"LocalRunner($workDir)"

  def withDefaultEnv(env: Map[String, String]): WithEnvironmentProcessRunner[LocalRunner] = {
    WithEnvironmentProcessRunner(this, env)
  }

  override def run(input: RunProcess): Future[RunProcessResult] = {
    val proc   = input.resolveEnv
    val logger = IterableLogger(proc)
    execute(proc, logger)

    input.output.stream match {
      case None => logger.fileResultFuture
      case Some(streamingSettings) =>
        val iter = streamingSettings.filterForErrors(logger.iterator)
        Future.successful(RunProcessResult(iter))
    }
  }

  def execute(proc: RunProcess, iterableLogger: IterableLogger): Future[Int] = {
    val builder: ProcessBuilder = Process(proc.command, workDir.map(_.toFile), proc.env.toSeq: _*)

    val future = {
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
        logger.debug(s"$proc completed with $code")
        iterableLogger.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        iterableLogger.complete(err)
    }
    future
  }
}
