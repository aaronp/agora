package jabroni.exec

case class RunProcess(command: List[String],
                      env: Map[String, String] = Map.empty,
                      successExitCodes: Set[Int] = Set(0),
                      // when streaming results, we will already have sent a status code header (success).
                      // if we exit w/ a non-success code, then we need to indicate the start of the error response
                      errorMarker: String = "*** _-={ E R R O R }=-_ ***") {
  def withEnv(key: String, value: String): RunProcess = copy(env = env.updated(key, value))
}

object RunProcess {
  def apply(first: String, theRest: String*): RunProcess = new RunProcess(first :: theRest.toList, Map[String, String]())
}

case class OperationResult(messages: List[String])

object OperationResult {
  def apply(first: String, theRest: String*) = new OperationResult(first :: theRest.toList)
}