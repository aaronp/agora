package jabroni.rest.client

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

/**
  * A client which will try multiple clients
  *
  * @param mkClients
  */
case class FallbackClient(mkClients: Iterable[RestClient])(implicit ec: ExecutionContext) extends RestClient {

  private def requestWith(request: HttpRequest, clients: Iterator[RestClient], failures: List[Throwable]): Future[HttpResponse] = {
    if (!clients.hasNext) {
      val exp = new Exception(s"No clients left after ${failures.size} failures")
      failures.foreach(exp.addSuppressed)
      Future.failed(exp)
    } else {
      val future = clients.next.send(request)
      future.recoverWith {
        case err => requestWith(request, clients, err :: failures)
      }
    }
  }

  override def send(request: HttpRequest): Future[HttpResponse] = {
    requestWith(request, mkClients.iterator, Nil)
  }

  override def close(): Unit = mkClients.foreach(_.close)
}
