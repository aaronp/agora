package jabroni.exec

import java.nio.file.{Path, StandardOpenOption}

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.StrictLogging
import jabroni.domain.io.implicits._
import jabroni.exec.log._

import scala.concurrent.Future
import scala.sys.process.{Process, ProcessBuilder}
import scala.util.{Failure, Success, Try}

object LocalRunner {
  def apply(workDir: Option[Path] = None,
            loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer): LocalRunner = {
    new LocalRunner(workDir, loggerForProcess)
  }
}

/**
  * Something which can run commands
  */
class LocalRunner(val workDir: Option[Path] = None,
                  val loggerForProcess: RunProcess => IterableLogger = IterableLogger.forProcess)(implicit mat: Materializer) extends ProcessRunner with StrictLogging {

  import mat._

  def withWorkDir(wd: Option[Path]): LocalRunner = {
    new LocalRunner(wd, loggerForProcess)
  }

  def withLogger(newLoggerForProcess: RunProcess => IterableLogger): LocalRunner = {
    new LocalRunner(workDir, newLoggerForProcess)
  }

  override def run(inputProc: RunProcess, inputFiles: List[Upload]) = {

    val processLogger = loggerForProcess(inputProc)

    logger.debug(s"Running $inputProc w/ ${inputFiles.size} uploads")

    /**
      * write down the multipart input(s)
      */
    val futures = inputFiles.map {
      case Upload(name, _, src, _) =>
        val dest = name.asPath
        if (!dest.exists) {
          val writeFut = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
          writeFut.onComplete {
            case res => logger.debug(s"Writing to $dest completed w/ $res")
          }
          writeFut.map(_ => dest)
        } else {
          Future.successful(dest)
        }
    }
    val inputsWritten: Future[List[Path]] = if (futures.isEmpty) {
      Future.successful(Nil)
    } else {
      Future.sequence(futures)
    }
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