package jabroni.exchange

import jabroni.api.client.{ClientRequest, SubmitJob}
import jabroni.api.exchange.SelectionMode
import jabroni.api.worker.{RequestWork, WorkerRequest}

trait WorkDispatcher {

  def dispatchAll(selection: SelectionMode.Selected, job: SubmitJob): Unit

}
