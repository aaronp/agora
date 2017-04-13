package jabroni.api
package client

import scala.concurrent.Future

trait JobScheduler {
  def send(request: ClientRequest): Future[ClientResponse]
}
