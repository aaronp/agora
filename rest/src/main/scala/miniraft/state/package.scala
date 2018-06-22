package miniraft

import agora.api.config.HostLocation

package object state {

  type NodeId   = String
  type LogIndex = Int

  def raftId(location: HostLocation) = location.asHostPort

  def isMajority(n: Int, total: Int): Boolean = {
    if (n == 0) {
      total == 0
    } else {
      n > (total >> 1)
    }
  }
}
