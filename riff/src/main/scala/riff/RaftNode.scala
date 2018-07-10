package riff

/**
  * Represents a follower, candidate or leader node
  */
sealed trait RaftNode extends HasNodeData {

  type Self <: RaftNode

  def name: String

  def state: RaftState

  protected def withData(newData: NodeData): Self

  def withPeers(peers: Map[String, Peer]): Self = withData(data.withPeers(peers))

  def withPeers(peers: Peer*): Self = withPeers(peers.map(p => p.name -> p).toMap)

  def updated(term: Int, newCommitIndex: Int = commitIndex): Self = withData(data.copy(currentTerm = term, commitIndex = newCommitIndex))

  override def toString: String = {
    val votedForString: String = this match {
      case FollowerNode(_, _, Some(name)) => name
      case FollowerNode(_, _, None) => "N/A"
      case _ => name
    }

    val peersString: String = if (peersByName.isEmpty) {
      ""
    } else {
      val rows: List[List[String]] = {
        val body = peersByName.values.toList.sortBy(_.name).map { peer =>
          import peer._
          List(peer.name, nextIndex, matchIndex, voteGranted).map(_.toString)
        }
        List("Peer", "Next Index", "Match Index", "Vote Granted") :: body
      }

      val maxColLengths = rows.map(_.map(_.length)).transpose.map(_.max)
      val indent = "\n" + (" " * 15)
      rows.map { row =>
        row.ensuring(_.size == maxColLengths.size).zip(maxColLengths).map {
          case (n, len) => n.padTo(len, ' ')
        }.mkString(" | ")
      }.mkString(indent,indent,"")
    }
    s"""        name : $name
       |       state : $state
       |current term : $currentTerm
       |   voted for : $votedForString
       |commit index : $commitIndex$peersString
       |""".stripMargin
  }
}

trait HasNodeData {
  def data: NodeData

  def currentTerm: Int = data.currentTerm

  def commitIndex: Int = data.commitIndex

  def peersByName: Map[String, Peer] = data.peersByName

  final def voteCount() = peersByName.values.count(_.voteGranted)
}

final case class NodeData(override val currentTerm: Int,
                          override val commitIndex: Int,
                          override val peersByName: Map[String, Peer]) extends HasNodeData {
  require(currentTerm > 0)

  override def data: NodeData = this

  def withPeers(peers: Map[String, Peer]): NodeData = copy(peersByName = peers)

  def withPeers(peers: Peer*): NodeData = withPeers(peers.map(p => p.name -> p).toMap)

  def inc: NodeData = updated(newTerm = currentTerm + 1)

  def updated(newTerm: Int = currentTerm, newIndex: Int = commitIndex): NodeData = copy(currentTerm = newTerm, commitIndex = newIndex)

}

object NodeData {

  def apply(): NodeData = new NodeData(
    currentTerm = 1,
    commitIndex = 0,
    peersByName = Map.empty
  )
}

/**
  * @param name
  */
final case class FollowerNode(name: String, override val data: NodeData, votedFor: Option[String]) extends RaftNode {

  override type Self = FollowerNode

  protected def withData(newData: NodeData): FollowerNode = copy(data = newData)

  /**
    * Append entries to this node, using the given time as a reply.
    *
    * The request will succeed if it is for the same term and expected commit index.
    *
    * If the request is for an older term it will be ignored
    *
    * If it is for a newer term then this node will assume there is a new leader (one for which it may not have voted)
    * and update its term.
    *
    * @param append
    * @param now
    * @tparam T
    * @return
    */
  def onAppendEntries[T](append: AppendEntries[T], now: Long): (FollowerNode, AppendEntriesReply) = {
    def reply(ok: Boolean) = AppendEntriesReply(name, append.from, now, currentTerm, ok, matchIndex = commitIndex)

    currentTerm match {
      case t if t == append.term =>
        this -> reply(append.commitIndex == commitIndex)
      case t if t < append.term =>
        // we're out-of-sync
        copy(data = data.updated(newTerm = append.term), votedFor = None) -> reply(false)
      case _ => // ignore
        this -> reply(false)
    }
  }

  def onRequestVote(requestFromCandidate: RequestVote, now: Long): (FollowerNode, RequestVoteReply) = {
    votedFor match {
      case None if requestFromCandidate.term > currentTerm =>
        val newState = copy(data = data.inc, votedFor = Option(requestFromCandidate.from))
        val reply = RequestVoteReply(from = name, to = requestFromCandidate.from, sent = now, term = currentTerm, granted = true)

        (newState, reply)
      case _ =>
        val newState = this
        val reply = RequestVoteReply(from = name, to = requestFromCandidate.from, sent = now, term = currentTerm, granted = false)

        (newState, reply)
    }
  }

  override val state: RaftState = RaftState.Follower

  def onElectionTimeout(now: Long): CandidateNode = CandidateNode(name, data.inc, now)
}

final case class CandidateNode(name: String, override val data: NodeData, electionTimedOutAt: Long) extends RaftNode {
  override val state: RaftState = RaftState.Candidate

  def clusterSize = peersByName.size + 1

  def onVoteReply(reply: RequestVoteReply): RaftNode = {
    peersByName.get(reply.from).fold(this: RaftNode) { peer =>
      if (reply.term == currentTerm) {
        val newPeer = peer.copy(voteGranted = reply.granted)

        val newTotalVotes = voteCount() + 1 + 1
        val newPeers = peersByName.updated(reply.from, newPeer)
        if (CandidateNode.hasQuorum(newTotalVotes, clusterSize)) {
          LeaderNode(name, data.copy(peersByName = newPeers))
        } else {
          withPeers(newPeers)
        }
      } else {
        // got a delinquent vote response from some other timeout - !
        // ignore it
        this
      }
    }
  }

  override type Self = CandidateNode

  protected def withData(newData: NodeData): CandidateNode = copy(data = newData)

}

object CandidateNode {
  def hasQuorum(totalVotes: Int, clusterSize: Int): Boolean = {
    require(clusterSize > 0)
    val requiredVotes = clusterSize / 2
    totalVotes > requiredVotes
  }
}

final case class LeaderNode(name: String, override val data: NodeData) extends RaftNode {
  override val state: RaftState = RaftState.Leader

  /**
    * This should be called *after* some data T has been written to the uncommitted journal.
    *
    * We should thus still have the same commit index until we get the acks back
    *
    * @param entries
    * @tparam T
    * @return
    */
  def append[T](entries: T) = {
    peersByName.collect {
      case (_, peer) if peer.matchIndex == commitIndex =>
        AppendEntries[T](
          from = name,
          to = peer.name,
          term = currentTerm,
          prevIndex = peer.matchIndex,
          prevTerm = currentTerm,
          entries = entries,
          commitIndex = commitIndex)
    }
  }

  override type Self = LeaderNode

  protected def withData(newData: NodeData): LeaderNode = copy(data = newData)
}


object RaftNode {

  def apply(nodeName: String): FollowerNode = FollowerNode(name = nodeName, data = NodeData(), None)
}