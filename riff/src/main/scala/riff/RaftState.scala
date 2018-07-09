package riff
sealed trait RaftState
object RaftState {
  def Follower = riff.Follower
  def Leader = riff.Leader
  def Candidate = riff.Candidate
}

case object Follower extends RaftState

case object Leader extends RaftState

case object Candidate extends RaftState
