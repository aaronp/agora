package jabroni.exec

import com.typesafe.scalalogging.LazyLogging
import jabroni.exec.model.RunProcess

import scala.sys.process.ProcessLogger

package object log extends LazyLogging {

  def logPrefix(proc: RunProcess) = {
    val full = proc.command.mkString(" ", " ", "").lines.mkString(" ")
    val commandLine = full.lastIndexOf("/") match {
      case -1 => full
      case n => full.splitAt(n)._2
    }
    if (commandLine.size > 30) {
      commandLine.take(20) + "..."
    } else {
      commandLine
    }
  }

  def loggingProcessLogger(prefix: String) = {
    ProcessLogger(
      (s: String) => logger.info(s"$prefix <OUT> $s"),
      (s: String) => logger.error(s"$prefix <ERR> $s"))
  }

}
