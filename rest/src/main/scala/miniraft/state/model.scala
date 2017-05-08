package miniraft.state


case class Term(t: Int) {
  def inc = Term(t + 1)
}

sealed trait NodeRole

case object Leader extends NodeRole

case object Follower extends NodeRole

case object Candidate extends NodeRole

case class LogEntry(term: Term, index: LogIndex, command: Command)

case class Log(entries: List[LogEntry] = Nil)

// this will likely be refactored to be a trait, allowing persistent storage
case class PersistentState(currentTerm: Term = Term(1),
                           votedFor: Option[NodeId] = None,
                           log: Log = Log())

case class VolatileState(commitIndex: LogIndex = 0, lastApplied: LogIndex = 0)

case class RaftNode(role: NodeRole, persistentState: PersistentState, volatileState: VolatileState)

object RaftNode {
  def apply(): RaftNode = {
    RaftNode(Follower, PersistentState(), VolatileState())
  }
}

case class LeaderState(viewById: Map[NodeId, NodeView])

object LeaderState {
  def apply(nodes: Set[NodeId], state: VolatileState) = {
    val initialView = NodeView(nextIndex = state.commitIndex + 1, matchIndex = 0)
    val initialViewState = nodes.map(_ -> initialView)
    new LeaderState(initialViewState.toMap)
  }
}

case class NodeView(nextIndex: LogIndex, matchIndex: LogIndex)