package jabroni.exec.model


/**
  *
  * @param command
  * @param env
  * @param successExitCodes
  * @param frameLength the frame length to use (if set) for delimiting output lines
  * @param errorMarker the marker which, if it appears in the standard output stream, will be followed by ProcessError
  *                    in json form
  * @param metadata    additional process data given as hints/advice to the process runner,
  *                    suh as tracking or session information
  */
case class RunProcess(command: List[String],
                      env: Map[String, String] = Map.empty,
                      successExitCodes: Set[Int] = Set(0),
                      frameLength: Option[Int] = None,
                      // when streaming results, we will already have sent a status code header (success).
                      // if we exit w/ a non-success code, then we need to indicate the start of the error response
                      errorMarker: String = RunProcess.DefaultErrorMarker,
                      metadata: Map[String, String] = Map.empty) {
  def addMetadata(first: (String, String), theRest: (String, String)*) = {
    val newMetadata = (first +: theRest).foldLeft(metadata) {
      case (map, (key, value)) => map.updated(key, value)
    }
    copy(metadata = newMetadata)
  }

  def withEnv(key: String, value: String): RunProcess = copy(env = env.updated(key, value))

  /**
    * Filters the iterator produced using this RunProcess for errors
    *
    * @param lineIter
    * @return
    */
  def filterForErrors(lineIter: Iterator[String]) = {
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

object RunProcess {
  def apply(first: String, theRest: String*): RunProcess = new RunProcess(first :: theRest.toList)

  val DefaultErrorMarker = "*** _-={ E R R O R }=-_ ***"

  val FormDataKey = "jabroni.exec.model.RunProcess"
}
