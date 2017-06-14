package miniraft.state.rest

import io.circe.{Decoder, Encoder}
import miniraft.{AppendEntries, AppendEntriesResponse, RequestVote, RequestVoteResponse}
import miniraft.state._

trait RaftJson {

  import io.circe.generic.auto._

  implicit val voteEncoder = exportEncoder[RequestVote].instance
  implicit val voteDecoder = exportDecoder[RequestVote].instance

  implicit def appendEntriesEncoder[T: Encoder] = exportEncoder[AppendEntries[T]].instance

  implicit def appendEntriesDecoder[T: Decoder] = exportDecoder[AppendEntries[T]].instance

  implicit val voteResponseEncoder = exportEncoder[RequestVoteResponse].instance
  implicit val voteResponseDecoder = exportDecoder[RequestVoteResponse].instance

  implicit val appendEntriesResponseEncoder = exportEncoder[AppendEntriesResponse].instance
  implicit val appendEntriesResponseDecoder = exportDecoder[AppendEntriesResponse].instance

}
