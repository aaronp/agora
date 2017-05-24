package jabroni.exec

import com.typesafe.scalalogging.StrictLogging
import io.circe
import io.circe.Json
import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.parser._
import jabroni.api.`match`.MatchDetails

import scala.util.Try

case class ProcessError(process: RunProcess,
                        exitCode: Option[Int],
                        matchDetails: Option[MatchDetails],
                        stdErr: List[String])

object ProcessError {

  def fromJsonString(json: String): Either[circe.Error, ProcessError] = {
    decode[ProcessError](json)
  }
}

case class ProcessException(error: ProcessError)
  extends Exception(s"${error.process} failed with ${error.exitCode}: ${error.stdErr.mkString("\n")}") {
  def json: Json = {
    import io.circe.syntax._
    error.asJson
  }
}

object ProcessException extends StrictLogging {
  def apply(process: RunProcess,
            exitCode: Try[Int],
            matchDetails: Option[MatchDetails],
            stdErr: List[String]) = {
    logger.error(stdErr.mkString(pprint.stringify(process) + s" \nfailed with\n${exitCode}\n", "\n", ""))
    new ProcessException(ProcessError(process, exitCode.toOption, matchDetails, stdErr))
  }
}