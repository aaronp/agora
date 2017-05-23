package jabroni.exec.log

import java.nio.file.Path

import jabroni.api.JobId
import jabroni.exec.RunProcess

import scala.concurrent.Future
import scala.sys.process.ProcessLogger

trait IterableLogger extends ProcessLogger {
  def complete(code: => Int): Unit

  def complete(exception: Throwable): Unit

  def iterator: Iterator[String]

  def exitCodeFuture: Future[Int]
}


object IterableLogger {

  def forProcess(proc: RunProcess): ProcessLoggers = apply("local", proc, None)

  def apply(jobName: String,
            proc: RunProcess,
            errorLimit: Option[Int]) = {
    new ProcessLoggers(jobName, proc, errorLimit)
  }

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

