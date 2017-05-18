package jabroni.exec

import java.nio.file.{Path, StandardOpenOption}

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import com.typesafe.scalalogging.StrictLogging
import jabroni.exec.log._

import scala.concurrent.Future
import scala.sys.process.{Process, ProcessBuilder}
import scala.util.{Failure, Success, Try}


object LocalRunner {

  def apply(uploadDir: Path,
            workDir: Option[Path],
            loggerForProcess: RunProcess => IterableLogger)(implicit mat: Materializer): LocalRunner = {
    new LocalRunner(uploadDir, workDir, loggerForProcess)
  }

}

/**
  * Something which can run commands
  */
class LocalRunner(val uploadDir: Path,
                  val workDir: Option[Path] = None,
                  val loggerForProcess: RunProcess => IterableLogger)(implicit mat: Materializer) extends ProcessRunner with StrictLogging {

  import mat._

  def copy(newUploadDir: Path = uploadDir,
           newWorkDir: Option[Path] = workDir,
           newLoggerForProcess: RunProcess => IterableLogger = loggerForProcess) = {
    new LocalRunner(newUploadDir, newWorkDir, newLoggerForProcess)
  }

  override def run(inputProc: RunProcess, inputFiles: List[Upload]) = {

    val processLogger = loggerForProcess(inputProc)
    logger.debug(
      s"""Executing w/ ${inputFiles.size} input(s) [${inputFiles.map(u => s"${u.name} ${u.size} bytes").mkString(",")}]:
         |    uploadDir  : $uploadDir
         |    logger     : $processLogger
         |$inputProc
         |
      """.stripMargin)
    /**
      * write down the multipart input(s)
      */
    val futures = inputFiles.map {
      case Upload(name, _, src) =>
        val dest = uploadDir.resolve(name)
        val writeFut = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))

        writeFut.onComplete {
          case res => logger.debug(s"Writing to $dest completed w/ $res")
        }
        writeFut.map(r => (dest, r))
    }
    val inputsWritten: Future[List[(Path, IOResult)]] = Future.sequence(futures)
    inputsWritten.map { _ =>
      val preparedProcess: RunProcess = insertEnv(inputProc)
      val builder: ProcessBuilder = {
        Process(preparedProcess.command, workDir.map(_.toFile), preparedProcess.env.toSeq: _*)
      }
      execute(builder, preparedProcess, processLogger)
    }
  }


  /**
    * Expose info about the configuration via environment variables
    */
  def insertEnv(inputProc: RunProcess) = {
    inputProc.copy(env = inputProc.env.
      updated("EXEC_WORK_DIR", workDir.map(_.toAbsolutePath.toString).getOrElse("")).
      updated("EXEC_UPLOAD_DIR", uploadDir.toAbsolutePath.toString)
    )
  }

  def execute(builder: ProcessBuilder, proc: RunProcess, iterableLogger: IterableLogger) = {

    val future = {
      val startedTry: Try[Process] = Try {
        builder.run(iterableLogger)
      }
      startedTry match {
        case Success(process) => Future(process.exitValue())
        case Failure(err) => Future.failed(err)
      }
    }

    future.onComplete {
      case Success(code) =>
        iterableLogger.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        iterableLogger.complete(err)
    }
    iterableLogger.iterator
  }

}