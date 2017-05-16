package jabroni.exec

import java.nio.file.{Path, StandardOpenOption}
import log._
import log.ProcessLoggers._
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.LazyLogging
import jabroni.exec.ProcessRunner._

import scala.concurrent.Future
import scala.util.{Failure, Success}


/**
  * Something which can run commands
  */
case class LocalRunner(workDir: Option[Path],
                       uploadDir: Path,
                       logDir: Option[Path],
                       errorLimit: Option[Int],
                       includeConsoleAppender: Boolean)(implicit mat: Materializer) extends ProcessRunner with LazyLogging {

  import mat._

  override def run(inputProc: RunProcess, inputFiles: List[Upload]) = {

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
      execute(insertEnv(inputProc), paths)
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

  def execute(proc: RunProcess, paths: List[Path]) = {
    logger.debug(s"Running $proc w/ ${paths.size} uploads ${paths.mkString(";")}")

    val stdOut: StreamLogger = StreamLogger()
    val stdOutIterator = stdOut.iterator
    val log: SplitLogger = newLogger(stdOut, logDir, errorLimit, includeConsoleAppender)
    val future = runUnsafe(proc, workDir.map(_.toFile), log)

    future.onComplete {
      case Success(code) => stdOut.complete(code)
      case Failure(err) =>
        logger.error(s"$proc failed with $err", err)
        stdOut.complete(-1)
    }
    stdOutIterator
  }

}