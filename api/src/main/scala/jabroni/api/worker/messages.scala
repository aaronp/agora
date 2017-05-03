package jabroni.api.worker

import io.circe.Json
import jabroni.api.exchange.SubmitJob


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
case class DispatchWork(subscription: SubscriptionKey, job: SubmitJob, remaining : Int) extends WorkerRequest {

  override def json: Json = {
    import io.circe.generic.auto._
    import io.circe.syntax._
    this.asJson
  }
}

case class DispatchWorkResponse(ok: Boolean) extends WorkerResponse

