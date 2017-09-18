package agora.exec.model

import agora.io.IterableSubscriber
import akka.http.scaladsl.model.HttpResponse
import akka.stream.Materializer

/**
  * @param successExitCodes the set of exit codes which are attribute to success
  * @param frameLength      the frame length to use (if set) for delimiting output lines
  * @param errorMarker      the marker which, if it appears in the standard output stream, will be followed by ProcessError
  *                         in json form
  */
case class StreamingSettings(successExitCodes: Set[Int] = Set(0),
                             frameLength: Option[Int] = None,
                             allowTruncation: Boolean = true,
                             // when streaming results, we will already have sent a status code header (success).
                             // if we exit w/ a non-success code, then we need to indicate the start of the error response
                             errorMarker: String = StreamingSettings.DefaultErrorMarker) {

  def asResult(httpResp: HttpResponse, defaultFrameLength: Int = 0)(implicit mat: Materializer): StreamingResult = {
    val bytes = httpResp.entity.dataBytes
    val len   = frameLength.getOrElse(defaultFrameLength)
    val iter  = IterableSubscriber.iterate(bytes, len, allowTruncation)
    RunProcessResult(filterForErrors(iter))
  }

  def isSuccessfulExitCode(code: Int) = successExitCodes.contains(code)

  /**
    * Filters the iterator produced using this RunProcess for errors
    *
    * @param lineIter
    * @return
    */
  def filterForErrors(lineIter: Iterator[String]): Iterator[String] = {
    lineIter.map {
      case line if line == errorMarker =>
        val json = lineIter.mkString("\n")
        ProcessError.fromJsonString(json) match {
          case Right(processError) => throw new ProcessException(processError)
          case Left(parseError) =>
            sys.error(s"Encountered the error marker '${errorMarker}', but couldn't parse '$json' : $parseError")
        }
      case ok => ok
    }
  }
}

object StreamingSettings {

  val DefaultErrorMarker = "*** _-={ E R R O R }=-_ ***"

}
