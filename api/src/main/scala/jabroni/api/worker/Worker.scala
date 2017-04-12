package jabroni.api
package worker

import scala.concurrent.Future

trait Worker {
  def requestWork(req: RequestWork): Future[RequestWorkResponse]

  def updateWorkRequest(id: WorkRequestId, req: RequestWork): Future[RequestWorkResponse]

  def cancelWorkRequest(id: WorkRequestId): Future[Boolean]
}
