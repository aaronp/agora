package riff
sealed trait NodeRole
object NodeRole {
  def Follower = riff.Follower
  def Leader = riff.Leader
  def Candidate = riff.Candidate
}

case object Follower extends NodeRole

case object Leader extends NodeRole

case object Candidate extends NodeRole
