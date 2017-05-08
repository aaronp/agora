package jabroni.api.exchange


import scala.concurrent.Future

trait JobPublisher {
  def send(request: ClientRequest): Future[ClientResponse] = request match {
    case req : SubmitJob => submit(req)
  }

  def submit(req: SubmitJob) : Future[ClientResponse] = send(req)
}
