package jabroni.exec.model


case class OperationResult(messages: List[String])

object OperationResult {
  def apply(first: String, theRest: String*) = new OperationResult(first :: theRest.toList)
}