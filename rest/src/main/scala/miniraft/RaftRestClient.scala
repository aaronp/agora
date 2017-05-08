package miniraft

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.circe.{Decoder, Encoder}
import jabroni.rest.client.RestClient
import miniraft.RaftHttp.{forAppend, forVote}

import scala.concurrent.Future
case class RestRaftClient(name: String, rest: RestClient)(implicit val sys: ActorSystem, mat: Materializer) extends Transport {

  import RestClient.implicits._

  override def onAppendEntries[T: Encoder : Decoder](append: AppendEntries[T]): Future[AppendEntriesResponse] = {
    //rest.send(forAppend(append)).flatMap(_.as[AppendEntriesResponse])
    ???
  }

  override def onRequestVote(vote: RequestVote): Future[RequestVoteResponse] = {
//    rest.send(forVote(vote)).flatMap(_.as[RequestVoteResponse])
    ???
  }
}

