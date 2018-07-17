package riff

package object raft {


  def format(node : RaftNode): String = {

    val votedForString: String = node match {
      case FollowerNode(_, Some(name)) => name
      case FollowerNode(_, None) => "N/A"
      case _ => node.name
    }

    val peersString: String = if (node.peersByName.isEmpty) {
      ""
    } else {
      val rows: List[List[String]] = {
        val body = node.peersByName.values.toList.sortBy(_.name).map { peer =>
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
      }.mkString(indent, indent, "")
    }

    import node._
    s"""        name : $name
       |        role : $role
       |current term : $currentTerm
       |   voted for : $votedForString
       |commit index : $uncommittedLogIndex$peersString
       |""".stripMargin
  }
}
