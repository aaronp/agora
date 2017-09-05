package agora.exec.log

import java.nio.file.Path

import agora.api.io.implicits._
import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.exec.model.{RunProcess, StreamingProcess}

import scala.concurrent.Future
import scala.sys.process.{FileProcessLogger, ProcessLogger}

/**
  * A pimped out process logger which can produce an iterator of output and return
  * a future of the exit code.
  *
  * It can also append loggers which will be notified of the output while the process is running,
  * but does not guarantee any output will be sent if added after the process has started.
  */
trait IterableLogger extends ProcessLogger {

  /** This is called by a ProcessRunner to 'complete' the process and fulfill the exit code
    *
    * @param code the exit code
    */
  def complete(code: => Int): Unit

  /** This is called by a ProcessRunner on an error case to 'complete' the process, potentially
    * before it is even started
    *
    * @param exception an error
    */
  def complete(exception: Throwable): Unit

  /** In the cas where an error occurs during output streaming,
    * the output may contain an 'error marker', followed by the ProcessError json.
    *
    * See [[agora.exec.model.RunProcess#errorMarker]]
    *
    * @return an iterator of standard output.
    */
  def iterator: Iterator[String]

  /** @return the optional match details associated with this process
    */
  def matchDetails: Option[MatchDetails]

  /** @return a Future completed when this process does
    */
  def exitCodeFuture: Future[Int]

  /** @param pl an additional process logger to attach
    * @return
    */
  def add(pl: ProcessLogger): IterableLogger

  final def addUnderDir(logDir: Path): IterableLogger = {
    val stdOut                          = logDir.resolve("std.out").toFile
    val stdOutLogger: FileProcessLogger = ProcessLogger(stdOut)
    val stdErr                          = logDir.resolve("std.err").toFile
    val stdErrLogger                    = ProcessLogger(stdErr)
    addStdOut(stdOutLogger).addStdErr(stdErrLogger)
  }

  final def addStdOut(pl: ProcessLogger): IterableLogger = add(JustStdOut(pl))

  final def addStdErr(pl: ProcessLogger): IterableLogger = add(JustStdErr(pl))

}

object IterableLogger {

  def forProcess(proc: StreamingProcess): ProcessLoggers = apply(proc, None, None)

  def apply(proc: StreamingProcess, matchDetails: Option[MatchDetails] = None, errorLimit: Option[Int] = None) = {
    new ProcessLoggers(proc, matchDetails, errorLimit)
  }

  def pathForJob(configPath: String, baseDir: Option[Path], jobId: JobId, fileNameOpt: Option[String]): Either[String, Path] = {

    baseDir match {
      case Some(dir) =>
        val logDir = dir.resolve(jobId)
        if (logDir.isDir) {
          fileNameOpt match {
            // we're done -- we found the dir!
            case None                                              => Right(logDir)
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
