package jabroni.exchange.actors

import java.util.UUID

import jabroni.api.client.{ClientRequest, SubmitJob, _}
import jabroni.exchange.ExchangeState

class ExchangeActor(initialState: ExchangeState) extends BaseActor {
  var state = initialState

  override def receive: Receive = {
    case req: ClientRequest =>
      onRequest(req)
  }

  def onRequest(clientRequest: ClientRequest) = clientRequest match {
    case job: SubmitJob =>
      val id = UUID.randomUUID()
      state = state.submitJob(id, job)
      sender ! SubmitJobResponse(id)
    case GetSubmission(id) => sender ! GetSubmissionResponse(id, state.getSubmission(id))
    case CancelSubmission(id) =>
      val (newState, removed) = state.removeSubmission(id)
      state = newState
      sender ! CancelSubmissionResponse(id, removed.isDefined)
    case GetMatchedWorkers(id, blockUntilMatched) =>

  }
}

object ExchangeActor {

}
