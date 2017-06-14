package miniraft.state.rest

import io.circe.{Decoder, Encoder, Json}
import agora.rest.client.RestClient
import miniraft.state.LogEntry

import scala.concurrent.Future

/** REST client to the support routes
  */
class RaftSupportClient[T: Encoder: Decoder](c: RestClient) extends RaftClient(c) {

  import RestClient.implicits._
  import client.executionContext
  import client.materializer

  def forceElectionTimeout(): Future[Boolean] = {
    client.send(RaftHttp.support.forceElectionTimeout).map(_.status.isSuccess())
  }

  def logs(from: Option[Int] = None, to: Option[Int] = None): Future[List[LogEntry[T]]] = {
    client.send(RaftHttp.support.logs(from, to)).flatMap(_.as[List[LogEntry[T]]]())
  }

  def state(): Future[NodeStateSummary] = {
    client.send(RaftHttp.support.state).flatMap(_.as[NodeStateSummary]())
  }

  def recentMessages(limit: Option[Int]): Future[List[Json]] = {
    client.send(RaftHttp.support.recentMessages(limit)).flatMap(_.as[List[Json]]())
  }
}
