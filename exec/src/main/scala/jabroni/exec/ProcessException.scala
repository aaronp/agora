package jabroni.exec

import io.circe
import io.circe.Json
import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.parser._

import scala.util.Try

case class ProcessError(process: RunProcess,
                        exitCode: Option[Int],
                        stdErr: List[String])

object ProcessError {

  def fromJsonString(json : String): Either[circe.Error, ProcessError] = {
    decode[ProcessError](json)
  }
}

case class ProcessException(error: ProcessError)
  extends Exception(s"${error.process} failed with ${error.exitCode}") {
  def json: Json = {
    import io.circe.syntax._
    error.asJson
  }
}

object ProcessException {
  def apply(process: RunProcess,
            exitCode: Try[Int],
            stdErr: List[String]) = {
    new ProcessException(ProcessError(process, exitCode.toOption, stdErr))
  }
}