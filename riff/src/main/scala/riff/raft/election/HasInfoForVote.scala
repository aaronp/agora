package riff.raft.election
import riff.raft.LogCoords
import simulacrum.typeclass

/**
  * Represents the data a raft node would need to expose in order to evaluate an election request
  *
  * The rules for granting a vote are:
  * 1) A vote isn't already granted for the term
  * 2) the term is greater than the node's current term
  * 3) the log index is greater than this node's log length
  * 3) the log term is greater than or equal to this node's log term
  *
  * @tparam T the type of whatever representation of a raft node we have
  */
@typeclass trait HasInfoForVote[T] {

  /**
    * Create a reply to the given vote request
    *
    * @param receivingRaftNode the raft node which is processing the vote request
    * @param forRequest the data from the vote request
    * @return the RequestVoteReply
    */
  def replyTo(receivingRaftNode: T, forRequest: RequestVoteData): RequestVoteReply = {
    def logStateOk = {
      val ourLogState = logState(receivingRaftNode)
      forRequest.lastLogTerm >= ourLogState.term &&
      forRequest.lastLogIndex >= ourLogState.index
    }

    val ourTerm = currentTerm(receivingRaftNode)
    val granted: Boolean = {
      forRequest.term >= ourTerm &&
      !hasAlreadyVotedInTerm(receivingRaftNode, forRequest.term) &&
      logStateOk
    }

    if (granted) {
      RequestVoteReply(ourTerm + 1, granted)
    } else {
      RequestVoteReply(ourTerm, false)
    }
  }

  /** @param raftNode the raft node which is processing the vote request
    * @return the current term of the node
    */
  def currentTerm(raftNode: T): Int

  /** @param raftNode the raft node which is processing the vote request
    * @param term the term we're voting in
    * @return true if the raft node has already voted in an election for this term
    */
  def hasAlreadyVotedInTerm(raftNode: T, term: Int): Boolean

  /** @param raftNode the raft node which is processing the vote request
    * @return the current state of our log
    */
  def logState(raftNode: T): LogCoords
}

object HasInfoForVote {

  /** @param currentTermValue the current term
    * @param logStateValue the current log state
    * @param votedInTerm the function used to determine if it voted in the given term
    * @tparam T the type for this HasInfoForVote
    * @return a HasInfoForVote for any type T
    */
  def const[T](term: Int, log: LogCoords)(votedInTerm: Int => Boolean) = new HasInfoForVote[T] {
    override def currentTerm(raftNode: T): Int = term

    override def hasAlreadyVotedInTerm(raftNode: T, term: Int): Boolean = votedInTerm(term)

    override def logState(raftNode: T): LogCoords = log
  }
}
