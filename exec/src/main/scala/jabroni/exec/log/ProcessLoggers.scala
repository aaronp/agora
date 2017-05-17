package jabroni.exec.log

import java.nio.file.Path

import com.typesafe.scalalogging.LazyLogging
import jabroni.api.JobId

import scala.sys.process.ProcessLogger

/**
  * Contains all the places where stuff will be logged
  */
case class ProcessLoggers(jobName: String,
                          logDirOpt: Option[Path],
                          errorLimit: Option[Int],
                          successErrorCodes: Set[Int]) extends LazyLogging {


  private val stdErrLog = StreamLogger()

  private val limitedErrorLog = errorLimit.fold(stdErrLog: ProcessLogger) { limit => LimitLogger(limit, stdErrLog) }
  private val stdOutLog = StreamLogger.forProcess(jobName) {
    case n if !successErrorCodes.contains(n) => stdErrLog.iterator.toStream
  }

  def processLogger: ProcessLogger = splitLogger

  def complete(code: Int) = splitLogger.complete(code)

  def iterator = stdOutLog.iterator

  def add(pl: ProcessLogger) = {
    splitLogger = splitLogger.add(pl)
  }

  private var splitLogger: SplitLogger = {
    val all = SplitLogger(JustStdOut(stdOutLog), JustStdErr(limitedErrorLog))
    logDirOpt.foreach { logDir =>
      val errLog = ProcessLogger(logDir.resolve("std.err").toFile)
      all.add(JustStdErr(errLog))

      val outLog = ProcessLogger(logDir.resolve("std.out").toFile)
      all.add(JustStdOut(outLog))
    }
    all
  }
}

object ProcessLoggers {


  def pathForJob(configPath: String, baseDir: Option[Path], jobId: JobId, fileNameOpt: Option[String]): Either[String, Path] = {
    import jabroni.domain.io.implicits._

    baseDir match {
      case Some(dir) =>
        val logDir = dir.resolve(jobId)
        if (logDir.isDir) {
          fileNameOpt match {
            // we're done -- we found the dir!
            case None => Right(logDir)
            case Some(fileName) if logDir.resolve(fileName).isFile => Right(logDir.resolve(fileName))
            case Some(fileName) =>
              def children = {
                val kids = logDir.children
                kids.map(_.toFile.getName).take(20).mkString(s"${logDir.getFileName.toString} contains ${kids.size} entries:\n", "\n", "\n...")
              }

              Left(s"Couldn't find ${fileName} Under ${logDir.toAbsolutePath}. Available files include ${children}")
          }
        } else {
          Left(s"Couldn't find job '${jobId}' under ${logDir.toAbsolutePath.toString}")
        }
      case None => Left(s"The '$configPath' isn't set, so no output is available")
    }
  }
}
