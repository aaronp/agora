package agora.exec.model

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import agora.api.`match`.MatchDetails

import scala.util.Try

case class ProcessException(error: ProcessError) extends Exception(s"${error.process} failed with ${error.exitCode}: ${error.stdErr.mkString("\n")}") {
  def json: Json = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    error.asJson
  }
}

object ProcessException extends StrictLogging {
  def apply(process: RunProcess, exitCode: Try[Int], matchDetails: Option[MatchDetails], stdErr: List[String]) = {
    logger.error(stdErr.mkString(s"$process \nfailed with\n${exitCode}\n", "\n", ""))
    new ProcessException(ProcessError(process, exitCode.toOption, matchDetails, stdErr))
  }
}
