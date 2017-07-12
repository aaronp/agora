package agora.exec.model

/**
  * Represents a container for messages produced while executing some operation
  * @param messages
  */
case class OperationResult(messages: List[String])

object OperationResult {
  def apply(first: String, theRest: String*) = new OperationResult(first :: theRest.toList)
}
