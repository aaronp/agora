package jabroni.exec.model

import io.circe
import io.circe.generic.auto.exportDecoder
import io.circe.parser._
import jabroni.api.`match`.MatchDetails

case class ProcessError(process: RunProcess,
                        exitCode: Option[Int],
                        matchDetails: Option[MatchDetails],
                        stdErr: List[String])

object ProcessError {

  def fromJsonString(json: String): Either[circe.Error, ProcessError] = {
    decode[ProcessError](json)
  }
}
