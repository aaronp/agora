package riff.raft.election

final case class RequestVote(from : String, to : String, term : Int, lastLogIndex : Int, lastLogTerm: Int)


object RequestVote {

}
