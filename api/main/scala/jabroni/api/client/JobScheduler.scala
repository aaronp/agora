package jabroni.api
package client

import scala.concurrent.{ExecutionContext, Future}

trait JobScheduler {
  def send(request: ClientRequest): Future[ClientResponse]

  def submit(req: SubmitJob)(implicit ec: ExecutionContext) = send(req).map(_.asInstanceOf[SubmitJobResponse])
}
