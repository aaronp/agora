package jabroni.exec.run


import java.nio.file.Path

import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import jabroni.exec.dao.UploadDao
import jabroni.exec.log._
import jabroni.exec.model.{RunProcess, Upload}

import scala.concurrent.Future
import scala.sys.process.{Process, ProcessBuilder}
import scala.util.{Failure, Success, Try}

object LocalRunner {
  def apply(uploadDao: UploadDao,
            workDir: Option[Path] = None,
            loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    new LocalRunner(uploadDao, workDir, loggerForProcess)
  }
}

/**
  * Something which can run commands
  */
class LocalRunner(val uploadDao: UploadDao,
                  val workDir: Option[Path] = None,
                  val loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer) extends ProcessRunner with StrictLogging {

  import mat._

  def withWorkDir(wd: Option[Path]): LocalRunner = {
    new LocalRunner(uploadDao, wd, loggerForProcess)
  }

  def withUploadDao(dao: UploadDao): LocalRunner = {
    new LocalRunner(dao, workDir, loggerForProcess)
  }

  def withLogger(newLoggerForProcess: RunProcess => IterableLogger): LocalRunner = {
    new LocalRunner(uploadDao, workDir, newLoggerForProcess)
  }

  override def run(inputProc: RunProcess, inputFiles: List[Upload]) = {

    val processLogger = loggerForProcess(inputProc)

    logger.debug(s"Running $inputProc w/ ${inputFiles.size} uploads")

    val inputsWritten = uploadDao.writeDown(inputFiles)
    inputsWritten.map { uploadResults =>
      val preparedProcess: RunProcess = insertEnv(inputProc, uploadResults)
      val builder: ProcessBuilder = {
        Process(preparedProcess.command, workDir.map(_.toFile), preparedProcess.env.toSeq: _*)
      }
      execute(builder, preparedProcess, processLogger)
    }
  }


  /**
    * Expose info about the configuration via environment variables
    */
  def insertEnv(inputProc: RunProcess, uploads: List[Path]) = {
    val newMap = uploads.foldLeft(inputProc.env) {
      case (map, path) =>
        val key = path.toFile.getName.map {
          case n if n.isLetterOrDigit => n.toUpper
          case _ => "_"
        }.mkString("")
        map.updated(key, path.toAbsolutePath.toString)
    }
    inputProc.copy(env = newMap)
  }

  def execute(builder: ProcessBuilder, proc: RunProcess, iterableLogger: IterableLogger): Iterator[String] = {

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