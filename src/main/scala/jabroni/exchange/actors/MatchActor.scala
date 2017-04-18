package jabroni.exchange.actors

import jabroni.api.WorkRequestId
import jabroni.api.worker.RequestWork
import jabroni.domain.Crud
import jabroni.exchange.ExchangeState

/**
  * Actor which pairs up work with workers
  * @param workerDao
  */
class MatchActor(workerDao : Crud[WorkRequestId, RequestWork]) extends BaseActor {
  import MatchActor._
  override def receive: Receive = {
    case OnWorkReceived(work, state) =>
  }
}

object MatchActor {
  case class OnWorkReceived(work : RequestWork, exchange : ExchangeState)

}