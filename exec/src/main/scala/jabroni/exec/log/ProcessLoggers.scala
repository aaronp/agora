package jabroni.exec.log

import java.nio.file.Path

import jabroni.api.JobId

import scala.sys.process.{FileProcessLogger, ProcessLogger}

object ProcessLoggers {


  /**
    *
    * @param stdOut
    * @param logDir
    * @param errorLimit
    * @param includeConsoleAppender
    * @return
    */
  def newLogger(stdOut: StreamLogger,
                logDir: Option[Path],
                errorLimit: Option[Int],
                includeConsoleAppender: Boolean): SplitLogger = {
    logDir.fold(SplitLogger(JustStdOut(stdOut))) { wd =>

      val errLog = {
        val fileLogger: FileProcessLogger = ProcessLogger(wd.resolve("std.err").toFile)
        errorLimit.fold(fileLogger: ProcessLogger) { limit => LimitLogger(limit, fileLogger) }
      }
      val outLog = ProcessLogger(wd.resolve("std.out").toFile)

      val log = SplitLogger(
        JustStdOut(stdOut),
        JustStdErr(errLog),
        JustStdOut(outLog)
      )
      if (includeConsoleAppender) {
        val console = ProcessLogger(
          (s: String) => Console.out.println(s"OUT: $s"),
          (s: String) => Console.err.println(s"ERR: $s"))
        log.add(console)
      } else {
        log
      }

    }
  }


  def pathForJob(configPath : String, baseDir: Option[Path], jobId: JobId, fileNameOpt: Option[String]): Either[String, Path] = {
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
