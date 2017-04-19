package jabroni.exchange

import jabroni.api._
import jabroni.api
import jabroni.api.{JobId, WorkRequestId}
import jabroni.api.client.{ClientRequest, SubmitJob}
import jabroni.api.exchange.JobPredicate
import jabroni.api.exchange.SelectionMode.Selected
import jabroni.api.worker.{RequestWork, WorkerRequest}
import org.scalatest.{Matchers, WordSpec}

class ExchangeStateTest extends WordSpec
  with Matchers
  with JobPredicate.LowPriorityImplicits {

  "ExchangeState.offerWork" should {
    "notify on matches" in {
      object d extends WorkDispatcher {
        override def dispatchAll(selection: Selected, job: SubmitJob): Unit = {

        }
      }
      val exchange: ExchangeState = ExchangeState(d)

//      val rw = RequestWork()
//      exchange.offerWork(nextWorkId, rw)

    }
  }
}
