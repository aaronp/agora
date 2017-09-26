package miniraft.state

case class NotTheLeaderException(currentLeader: Option[NodeId], roleId: NodeId, currentState: String, clusterSize: Int)
    extends Exception
