package agora.rest.stream

case class StreamCancelledException(name: String) extends ExceptionInInitializerError(s"$name cancelled")
