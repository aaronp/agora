package jabroni.exec

import java.nio.file.{Path, StandardOpenOption}

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import com.typesafe.scalalogging.StrictLogging
import jabroni.exec.ProcessRunner._
import jabroni.exec.log._

import scala.concurrent.Future
import scala.util.{Failure, Success}


/**
  * Something which can run commands
  */
case class LocalRunner(uploadDir: Path,
                       description: String = "process",
                       workDir: Option[Path] = None,
                       logDir: Option[Path] = None,
                       errorLimit: Option[Int] = None,
                       includeConsoleAppender: Boolean = true)(implicit mat: Materializer) extends ProcessRunner with StrictLogging {

  import mat._

  override def run(inputProc: RunProcess, inputFiles: List[Upload]) = {

    logger.debug(
      s"""Executing w/ ${inputFiles.size} input(s) [${inputFiles.map(u => s"${u.name} ${u.size} bytes").mkString(",")}]:
         |    workDir    : $workDir
         |    uploadDir  : $uploadDir
         |    logDir     : $logDir
         |    errorLimit : $errorLimit
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
    inputsWritten.map { (list: List[(Path, IOResult)]) =>

      val paths = list.map {
        case (path, res) => {
          require(res.wasSuccessful, s"Writing to $path failed : $res")
          path
        }
      }
      execute(description, insertEnv(inputProc), paths)
    }
  }


  /**
    * Expose info about the configuration via environment variables
    */
  def insertEnv(inputProc: RunProcess) = {
    inputProc.copy(env = inputProc.env.
      updated("EXEC_WORK_DIR", workDir.map(_.toAbsolutePath.toString).getOrElse("")).
      updated("EXEC_UPLOAD_DIR", uploadDir.toAbsolutePath.toString).
      updated("EXEC_LOG_DIR", logDir.map(_.toAbsolutePath.toString).getOrElse(""))
    )
  }

  def execute(name: String, proc: RunProcess, paths: List[Path]) = {
    val loggers = ProcessLoggers(name, logDir, errorLimit, includeConsoleAppender, proc.successExitCodes)
    val future = runUnsafe(proc, workDir.map(_.toFile), loggers.splitLogger)

    future.onComplete {
      case Success(code) => loggers.splitLogger.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        loggers.splitLogger.complete(-1)
    }
    loggers.stdOutLog.iterator
  }

}