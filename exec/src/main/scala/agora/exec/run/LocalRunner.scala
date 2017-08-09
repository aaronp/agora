package agora.exec.run

import java.nio.file.Path

import agora.exec.log._
import agora.exec.model.{RunProcess, RunProcessAndSave, RunProcessAndSaveResponse}
import agora.exec.run.ProcessRunner.ProcessOutput
import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.{Process, ProcessBuilder, ProcessLogger}
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Something which can execute [[RunProcess]]
  */
class LocalRunner(val workDir: Option[Path] = None, val defaultEnv: Map[String, String])(implicit ec: ExecutionContext) extends ProcessRunner with StrictLogging {

  override def toString = s"LocalRunner($workDir, $defaultEnv)"

  /**
    * @param newLogger
    * @return a new LocalRunner which will use the logger produced by this function
    */
  def withLogger(newLogger: IterableLogger => IterableLogger): LocalRunner = {
    val parent = this
    new LocalRunner(workDir, defaultEnv) {
      override def mkLogger(proc: RunProcess): IterableLogger = {
        newLogger(parent.mkLogger(proc))
      }
    }
  }

  def asByteIterator(runProc: RunProcess): Source[ByteString, NotUsed] = {
    def run = {
      try {
        execute(runProc).iterator
      } catch {
        case NonFatal(err) =>
          logger.error(s"Error executing $runProc: $err")
          throw err
      }
    }

    Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
  }

  override def run(proc: RunProcess): ProcessOutput = {
    val iter = proc.filterForErrors(execute(proc).iterator)
    Future.successful(iter)
  }

  override def runAndSave(proc: RunProcessAndSave): Future[RunProcessAndSaveResponse] = {
    val logger = execute(proc.process)
    logger.exitCodeFuture.map { exitCode =>
      RunProcessAndSaveResponse(exitCode, proc.workspaceId, proc.stdOutFileName, None)
    }
  }

  private var additionalLoggers = List[ProcessLogger]()

  /** Adds the given logger to be notified when processes are run
    *
    * @param logger the logger to add for all processes used by this runner
    * @return the local runner instance (builder pattern)
    */
  def add(logger: ProcessLogger) = {
    additionalLoggers = logger :: additionalLoggers
    this
  }

  def remove(logger: ProcessLogger) = additionalLoggers = additionalLoggers diff List(logger)

  def mkLogger(proc: RunProcess): IterableLogger = {
    additionalLoggers.foldLeft(IterableLogger.forProcess(proc)) {
      case (lgr, next) => lgr.add(next)
    }
  }

  def execute(preparedProcess: RunProcess): IterableLogger = {
    val env                     = (defaultEnv ++ preparedProcess.env).toSeq
    val builder: ProcessBuilder = Process(preparedProcess.command, workDir.map(_.toFile), env: _*)
    execute(builder, preparedProcess)
  }

  def execute(builder: ProcessBuilder, proc: RunProcess): IterableLogger = {
    val iterableLogger: IterableLogger = mkLogger(proc)
    execute(builder, proc, iterableLogger)
    iterableLogger
  }

  def execute(builder: ProcessBuilder, proc: RunProcess, iterableLogger: IterableLogger): Future[Int] = {
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
        iterableLogger.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        iterableLogger.complete(err)
    }
    future
  }
}
