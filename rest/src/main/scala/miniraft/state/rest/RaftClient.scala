package miniraft.state.rest

import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}
import agora.rest.client.{RestClient, RetryClient}
import miniraft.state._
import io.circe.generic.auto._
import miniraft._

import scala.concurrent.Future

/**
  * A RaftEndpoint via a RestService
  */
class RaftClient[T: Encoder: Decoder](val client: RestClient) extends RaftEndpoint[T] with LazyLogging {

  import RestClient.implicits._
  import client.executionContext
  import client.materializer

  override def onVote(vote: RequestVote): Future[RequestVoteResponse] = {
    client
      .send(RaftHttp(vote))
      .flatMap(_.as[RequestVoteResponse] {
        case (body, resp, err) =>
          logger.error(s"Sending $vote to $client returned ${resp.status} ${body.getOrElse("")}: $err")
          client match {
            case retry: RetryClient =>
              retry.reset()
              onVote(vote)
            case _ => throw err
          }
      })
  }

  override def onAppend(append: AppendEntries[T]): Future[AppendEntriesResponse] = {
    client
      .send(RaftHttp(append))
      .flatMap(_.as[AppendEntriesResponse] {
        case (body, resp, err) =>
          logger.error(s"Sending $append to $client returned ${resp.status} ${body.getOrElse("")}: $err")
          client match {
            case retry: RetryClient =>
              retry.reset()
              onAppend(append)
            case _ => throw err
          }
      })
  }

  override def toString = s"RaftClient($client)"
}

object RaftClient {
  def apply[T: Encoder: Decoder](client: RestClient) = new RaftClient(client)
}
