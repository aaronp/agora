package jabroni.api.worker

import io.circe.Json
import jabroni.api.exchange.SubmitJob
import jabroni.api.{JobId, WorkRequestId}


/**
  * Represents a request requested by the worker to the exchange
  */
sealed trait WorkerRequest {
  def json: Json
}

sealed trait WorkerResponse

/**
  * Represents a message from the exchange to the worker
  */
case class DispatchWork(workRequestId: WorkRequestId, jobId: JobId, job: SubmitJob) extends WorkerRequest {

  override def json: Json = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    this.asJson
  }
}

case class DispatchWorkResponse(ok: Boolean) extends WorkerResponse

