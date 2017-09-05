package miniraft

import agora.api.worker.HostLocation

package object state {

  type NodeId   = String
  type LogIndex = Int

  def raftId(location: HostLocation) = {
    import location._
    s"${host}:${port}"
  }

  object AsLocation {

    val HostPort = "(.*):(\\d+)".r
    def unapply(id: String): Option[HostLocation] = {
      id match {
        case HostPort(h, p) => Option(HostLocation(h, p.toInt))
        case _              => None
      }
    }
  }

  def isMajority(n: Int, total: Int): Boolean = {
    if (n == 0) {
      total == 0
    } else {
      n > (total >> 1)
    }
  }
}
