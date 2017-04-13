package jabroni.exchange.actors

import jabroni.api.WorkRequestId
import jabroni.api.worker.RequestWork
import jabroni.exchange.{ExchangeState, Crud}

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