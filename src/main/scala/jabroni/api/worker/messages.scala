package jabroni.api.worker

import jabroni.api.client.SubmitJob
import jabroni.api.WorkRequestId
import jabroni.api.exchange.JobPredicate
import jabroni.api.json.JMatcher


sealed trait WorkerRequest

case class RequestWork(worker: WorkerDetails,
                       workMatcher: JMatcher,
                       itemsRequested: Int) extends WorkerRequest {
  require(itemsRequested > 0)

  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def dec = copy(itemsRequested = itemsRequested - 1)

  def take(n: Int) = copy(itemsRequested = itemsRequested - n)
}

case class UpdateWorkItems(id: WorkRequestId, itemsRequested: Int) extends WorkerRequest

//case class UpdateWorkRequest(id : JobId, req : RequestWork) extends WorkerRequest

sealed trait WorkerResponse

case class RequestWorkResponse(id: WorkRequestId) extends WorkerResponse

case class UpdateWorkItemsResponse(id: WorkRequestId, newItemsRequested: Int) extends WorkerResponse
