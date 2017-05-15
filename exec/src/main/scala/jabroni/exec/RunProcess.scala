package jabroni.exec

case class RunProcess(command: List[String], env: Map[String, String]) {
  def withEnv(key: String, value: String): RunProcess = copy(env = env.updated(key, value))
}

object RunProcess {
  def apply(first: String, theRest: String*): RunProcess = new RunProcess(first :: theRest.toList, Map[String, String]())
}
