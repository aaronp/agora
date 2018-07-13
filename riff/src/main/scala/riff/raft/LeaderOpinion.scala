package riff.raft

/**
  * Represents what a node things the leader is at a given time.
  *
  *
  */
sealed trait LeaderOpinion {
  def name: String
  def term: Int
}

object LeaderOpinion {

  final case class Unknown(name: String, term: Int) extends LeaderOpinion

  final case class ImACandidate(name: String, term: Int) extends LeaderOpinion

  final case class ImTheLeader(name: String, term: Int) extends LeaderOpinion

  final case class TheLeaderIs(name: String, term: Int) extends LeaderOpinion

}
