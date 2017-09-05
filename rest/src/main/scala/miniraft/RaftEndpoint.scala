package miniraft

import agora.rest.client.RestClient
import io.circe.Encoder
import miniraft.state.rest.{RaftHttp, RaftJson}

import scala.concurrent.Future

/**
  * A generic representatino of a raft node -- simply something which can
  * respond to RaftRequest messages
  *
  * @tparam T
  */
trait RaftEndpoint[T] {
  def onRequest(req: RaftRequest): Future[RaftResponse] = {
    req match {
      case vote: RequestVote        => onVote(vote)
      case append: AppendEntries[T] => onAppend(append)
    }
  }

  def onVote(vote: RequestVote): Future[RequestVoteResponse]

  def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse]
}

object RaftEndpoint {

  def apply[T: Encoder](client: RestClient) = new Rest[T](client)

  /** Represents an endpoint by sending REST requests
    */
  class Rest[T: Encoder](client: RestClient) extends RaftEndpoint[T] with RaftJson {

    import RestClient.implicits._
    import client.executionContext
    private implicit val materializer = client.materializer

    override def onVote(vote: RequestVote): Future[RequestVoteResponse] = {
      client.send(RaftHttp(vote)).flatMap(_.as[RequestVoteResponse]())
    }

    override def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse] = {
      client.send(RaftHttp(append)).flatMap(_.as[AppendEntriesResponse]())
    }
  }

}
