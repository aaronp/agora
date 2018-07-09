package riff

/**
  * The view of a peer in the cluster
  * @param name
  * @param nextIndex
  * @param matchIndex
  * @param voteGranted
  * @param lastRpcSent
  * @param lastHeartbeatSent
  */
case class Peer(name: String,
                nextIndex: Int,
                matchIndex: Int,
                voteGranted: Boolean,
                lastRpcSent : Long,
                lastHeartbeatSent : Long) {
  require(nextIndex > 0)
  require(matchIndex >= 0)
}

object Peer {


  def initial(peerName : String) = {
    Peer(name = peerName,
      nextIndex = 1,
      matchIndex = 0,
      voteGranted = false,
      lastRpcSent = 0,
      lastHeartbeatSent = 0
    )
  }
}




