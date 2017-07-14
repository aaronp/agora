package agora.exec

import agora.api.json.{JFilter, JMatcher, JPart, JPath}

package object session {
  type SessionId = String

  def matchServerSession(id: SessionId): JMatcher = {
    import agora.api.Implicits._
    JPath(JPart("session"), "id" === id).asMatcher
  }
  def matchClientSession(id: SessionId): JMatcher = {
    import agora.api.Implicits._
    ("session" === id).asMatcher
  }

  def safeId(sessionId: SessionId) = sessionId.filter(_.isLetterOrDigit)
}
