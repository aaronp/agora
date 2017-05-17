package jabroni.exec

import com.typesafe.scalalogging.LazyLogging

import scala.sys.process.ProcessLogger

package object log extends LazyLogging {

  lazy val loggingProcessLogger =
    ProcessLogger(
      (s: String) => logger.debug(s"OUT: $s"),
      (s: String) => logger.error(s"ERR: $s"))

}
