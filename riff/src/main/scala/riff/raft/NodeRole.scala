package riff.raft

sealed trait NodeRole
object NodeRole {
  def Follower = Follower
  def Leader = Leader
  def Candidate = Candidate
}

case object Follower extends NodeRole

case object Leader extends NodeRole

case object Candidate extends NodeRole
