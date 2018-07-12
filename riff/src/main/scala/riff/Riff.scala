package riff

import cats.free.Free
import crud.api.Lift
import riff.Riff.{AppendOptions, AppendResult}
import riff.raft.{LeaderNode, RaftState}

sealed trait Riff[A]
case class Append[T](data : T, options : AppendOptions) extends Riff[AppendResult]

object Riff {
  final case class AppendOptions(requiredNrToReply : Int)
  final case class AppendResult(committedTo : Set[String])

  def append() = {
    val state: RaftState[String] = RaftState.of("A")

    Free.liftF(RaftState.AppendLogEntry)
  }
}