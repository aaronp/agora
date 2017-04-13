package jabroni.api
package worker

import scala.concurrent.Future

/**
  * Represents the client of the exchange which will as for work and response to client requests
  *
  * It will then hand that work off to an executor
  */
trait WorkerOfferClient {
  def requestWork(req: RequestWork): Future[RequestWorkResponse]

  def updateWorkRequest(id: WorkRequestId, req: RequestWork): Future[RequestWorkResponse]

  def cancelWorkRequest(id: WorkRequestId): Future[Boolean]
}
