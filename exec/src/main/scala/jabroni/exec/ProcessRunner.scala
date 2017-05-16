package jabroni.exec

import java.nio.file.{Path, StandardOpenOption}

import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import com.typesafe.scalalogging.LazyLogging
import jabroni.domain.IterableSubscriber
import jabroni.exec.ProcessLoggers.{JustStdOut, SplitLogger, StreamLogger}
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartBuilder

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls
import scala.sys.process.{FileProcessLogger, ProcessLogger}
import scala.util._
import scala.sys.process._

/**
  * prepresents something which can be run
  */
trait ProcessRunner {
  def run(proc: RunProcess, inputFiles: List[Upload]): ProcessRunner.ProcessOutput

  def run(cmd: String, theRest: String*): ProcessRunner.ProcessOutput = run(RunProcess(cmd :: theRest.toList, Map[String, String]()), Nil)
}

object ProcessRunner {
  type ProcessOutput = Future[Iterator[String]]

  /**
    * Creates a process runner to run under the given directory
    *
    */
  def apply(uploadDir: Path,
            workDir: Option[Path] = None,
            logDir: Option[Path] = None,
            errorLimit: Option[Int] = None)(implicit mat: Materializer) = {
    new Runner(workDir,
      uploadDir,
      logDir,
      errorLimit)
  }

  /**
    * @param worker the worker client used to send requests
    * @return a runner which executes stuff remotely
    */
  def apply(worker: ExchangeClient,
            maximumFrameLength: Int,
            allowTruncation: Boolean)(implicit map: Materializer,
                                      uploadTimeout: FiniteDuration): ProcessRunner with AutoCloseable = {
    RemoteRunner(worker, maximumFrameLength, allowTruncation)
  }

  case class RemoteRunner(exchange: ExchangeClient,
                          maximumFrameLength: Int,
                          allowTruncation: Boolean)(implicit mat: Materializer,
                                                    uploadTimeout: FiniteDuration) extends ProcessRunner with AutoCloseable {

    import mat._

    override def run(proc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
      import io.circe.generic.auto._

      val reqBuilder = inputFiles.foldLeft(MultipartBuilder().json(proc)) {
        case (builder, Upload(name, len, src)) =>
          builder.fromSource(name, len, src, fileName = name)
      }

      import jabroni.api.Implicits._
      val (_, workerResponses) = exchange.enqueueAndDispatch(proc.asJob) { worker =>
        reqBuilder.formData.flatMap(worker.sendMultipart)
      }

      workerResponses.map { completedWork =>
        val resp = completedWork.onlyResponse
        IterableSubscriber.iterate(resp.entity.dataBytes, maximumFrameLength, allowTruncation)
      }
    }

    override def close(): Unit = exchange.close()
  }

  /**
    * Something which can run commands
    */
  case class Runner(workDir: Option[Path],
                    uploadDir: Path,
                    logDir: Option[Path],
                    errorLimit: Option[Int])(implicit mat: Materializer) extends ProcessRunner with LazyLogging {

    import mat._

    override def run(proc: RunProcess, inputFiles: List[Upload]) = {
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
      inputsWritten.map { list =>
        val paths = list.map {
          case (path, res) => {
            require(res.wasSuccessful, s"Writing to $path failed : $res")
            path
          }
        }
        logger.debug(s"Running $proc w/ ${paths.size} uploads ${paths.mkString(";")}")

        val stdOut: StreamLogger = StreamLogger()
        val stdOutIterator = stdOut.iterator
        val log: SplitLogger = newLogger(stdOut, logDir, errorLimit)
        val future = runUnsafe(proc, workDir.map(_.toFile), log)

        future.onComplete {
          case Success(code) => stdOut.complete(code)
          case Failure(err) => stdOut.complete(-1)
        }
        stdOutIterator
      }
    }

  }


  def newLogger(stdOut: StreamLogger,
                logDir: Option[Path],
                errorLimit: Option[Int]): SplitLogger = {
    logDir.fold(SplitLogger(JustStdOut(stdOut))) { wd =>
      import ProcessLoggers._
      val errLog = {
        val fileLogger: FileProcessLogger = ProcessLogger(wd.resolve("std.err").toFile)
        errorLimit.fold(fileLogger: ProcessLogger) { limit => LimitedLogger(limit, fileLogger) }
      }
      val outLog = ProcessLogger(wd.resolve("std.out").toFile)

      SplitLogger(
        JustStdOut(stdOut),
        JustStdErr(errLog),
        JustStdOut(outLog)
      )
    }
  }

  def runUnsafe(proc: RunProcess,
                workDir: Option[java.io.File],
                log: SplitLogger)(implicit ec: ExecutionContext): Future[Int] = {

    val startedTry: Try[Process] = Try {
      Process(proc.command, workDir, proc.env.toSeq: _*).run(log)
    }
    startedTry match {
      case Success(process) =>
        val future = Future(process.exitValue())
        future.onComplete { res =>
          log.flush()
          log.close()
        }
        future
      case Failure(err) =>
        log.flush()
        log.close()
        Future.failed(err)
    }
  }

}
