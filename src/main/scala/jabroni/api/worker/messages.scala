package jabroni.api.worker

import io.circe.Json
import jabroni.api.client.SubmitJob
import jabroni.api.{JobId, WorkRequestId}
import jabroni.api.exchange.{JobPredicate, SelectionMode}
import jabroni.api.json.JMatcher

/**
  * Represents a message from the exchange to the worker
  */
sealed trait ExchangeRequest
sealed trait ExchangeResponse

case class DispatchWork(workRequestId: WorkRequestId, jobId: JobId, job: SubmitJob) extends ExchangeRequest
case class DispatchWorkResponse(ok : Boolean) extends ExchangeResponse


/**
  * Represents a request requested by the worker to the exchange
  */
sealed trait WorkerRequest {
  def json: Json
}

case class RequestWork(worker: WorkerDetails,
                       workMatcher: JMatcher,
                       itemsRequested: Int) extends WorkerRequest {
  require(itemsRequested > 0)

  def matches(job: SubmitJob)(implicit m: JobPredicate): Boolean = m.matches(job, this)

  def dec = copy(itemsRequested = itemsRequested - 1)

  def take(n: Int) = copy(itemsRequested = itemsRequested - n)

  override def json: Json = {
    import io.circe.syntax._
    this.asJson
  }
}

case class UpdateWorkItems(id: WorkRequestId, itemsRequested: Int) extends WorkerRequest {
  override def json: Json = {
    import io.circe.syntax._
    this.asJson
  }
}

//case class UpdateWorkRequest(id : JobId, req : RequestWork) extends WorkerRequest

sealed trait WorkerResponse

case class RequestWorkResponse(id: WorkRequestId) extends WorkerResponse

case class UpdateWorkItemsResponse(id: WorkRequestId, newItemsRequested: Int) extends WorkerResponse
