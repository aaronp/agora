package agora.exec.session

sealed trait SessionMessage
case class CloseEncoding(close: SessionId) extends SessionMessage
case class OpenEncoding(open: SessionId)   extends SessionMessage
