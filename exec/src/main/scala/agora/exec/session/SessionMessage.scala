package agora.exec.session

import agora.api.Implicits._
import agora.api.exchange.{SubmissionDetails, SubmitJob, WorkSubscription}
import agora.api.json.{JPart, JPath}
import agora.exec.model.RunProcess
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}

sealed trait SessionMessage

object SessionMessage {

  implicit object encoder extends Encoder[SessionMessage] {
    override def apply(a: SessionMessage): Json = a match {
      case msg: OpenSession  => implicitly[Encoder[OpenSession]].apply(msg)
      case msg: CloseSession => implicitly[Encoder[CloseSession]].apply(msg)
      case msg: UseSession   => implicitly[Encoder[UseSession]].apply(msg)
    }
  }

  implicit val decoder: Decoder[SessionMessage] = {
    (implicitly[Decoder[CloseSession]]
      .map(x => x: SessionMessage))
      .or(implicitly[Decoder[OpenSession]].map(x => x: SessionMessage))
      .or(implicitly[Decoder[UseSession]].map(x => x: SessionMessage))
  }
}

case class CloseSession(close: SessionId) extends SessionMessage

object CloseSession {
  def prepareSubscription(sessionId: SessionId, subscription: WorkSubscription) = {
    val criteria = ("close" === sessionId).asMatcher
    subscription.withPath("close").matchingJob(criteria).append("session", SessionState(sessionId, Nil, 0))
  }
  def asJob(id: SessionId)(implicit details: SubmissionDetails = SubmissionDetails()) = {
    val orig   = SubmitJob(details, CloseSession(id))
    val withId = orig.add("session" -> id)

    withId.withAwaitMatch(false).matching(matchServerSession(id) and "path" === "close")
  }
}

case class OpenSession(open: SessionId) extends SessionMessage

object OpenSession {
  def prepareSubscription(subscription: WorkSubscription) = {
    subscription.withPath("open").matchingJob(JPath("open").asMatcher)
  }

  def asJob(id: SessionId)(implicit details: SubmissionDetails = SubmissionDetails()) = {
    SubmitJob(details, OpenSession(id)).withAwaitMatch(false).matching(("path" === "open").asMatcher)
  }
}

case class UseSession(session: SessionId) extends SessionMessage

object UseSession {
  private val ExecName   = "exec"
  private val UploadName = "upload"

  val prepareExecSubscription   = prepareSubscription(ExecName) _
  val prepareUploadSubscription = prepareSubscription(UploadName) _

  private def prepareSubscription(name: String)(subscription: WorkSubscription, sessionId: SessionId) = {
    subscription
      .append("session", SessionState(sessionId, Nil, 0))
      .matchingSubmission(matchClientSession(sessionId))
      .append("name", name)
      .withPath(s"$name-${safeId(sessionId)}")
  }

  def asExecJob(id: SessionId, proc: RunProcess)(implicit details: SubmissionDetails = SubmissionDetails()) = {
    val criteria = matchServerSession(id) and "name" === ExecName
    proc.asJob(details).add("session" -> id).matching(criteria)
  }
  def asUploadJob(id: SessionId)(implicit details: SubmissionDetails = SubmissionDetails()) = {
    val criteria = matchServerSession(id) and "name" === UploadName
    UseSession(id).asJob(details).add("session" -> id).matching(criteria)
  }

}
