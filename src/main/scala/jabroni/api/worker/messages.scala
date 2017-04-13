package jabroni.api.worker

import jabroni.api.client.SubmitJob
import jabroni.api.WorkRequestId
import jabroni.api.exchange.Matcher
import jabroni.api.json.JsonMatcher


sealed trait WorkerRequest

case class RequestWork(worker: WorkerDetails, workMatcher: JsonMatcher, itemsRequested: Int) extends WorkerRequest {
  def matches(job: SubmitJob)(implicit m : Matcher) : Boolean =  m.matches(job, this)
}

case class UpdateWorkItems(id: WorkRequestId, itemsRequested: Int) extends WorkerRequest

//case class UpdateWorkRequest(id : JobId, req : RequestWork) extends WorkerRequest

sealed trait WorkerResponse

case class RequestWorkResponse(id: WorkRequestId) extends WorkerResponse

case class UpdateWorkItemsResponse(id: WorkRequestId, newItemsRequested: Int) extends WorkerResponse
