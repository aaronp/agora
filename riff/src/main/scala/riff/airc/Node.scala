package riff.airc

import cats.free._
import riff.raft._



object dsl {
  import leaderAlgebra._

  type Dsl[A] = FreeApplicative[LeaderAppend, A]

  private def lift[A](value : LeaderAppend[A]) : Dsl[A] = {
    FreeApplicative.lift[LeaderAppend, A](value)
  }

  def appendData[T](data: T, leader: LeaderNode): Dsl[LogCoords] = {
    lift(AppendData[T](data, leader))
  }

  def insert[T](entry: AppendEntries[T]): Dsl[LogCoords] = lift(InsertEntry(entry))
  def notifyCommitted(coords: LogCoords): Dsl[LogCoords] = lift(NotifyCommitted(coords))
  def notifyPeer[T](entry: AppendEntries[T], leader: LeaderNode): Dsl[AppendEntriesReply] = lift(NotifyPeer(entry: AppendEntries[T], leader: LeaderNode))
}

object leaderAlgebra {

  sealed trait LeaderAppend[A]

  final case class AppendData[T](data: T, leader: LeaderNode) extends LeaderAppend[LogCoords]

  final case class InsertEntry[T](entry: AppendEntries[T]) extends LeaderAppend[LogCoords]

  final case class NotifyCommitted[T](coords: LogCoords) extends LeaderAppend[LogCoords]

  final case class NotifyPeer[T](entry: AppendEntries[T], leader: LeaderNode) extends LeaderAppend[AppendEntriesReply]

}

object runobs {
  import leaderAlgebra._
  import dsl._

  def genAppend[T](data : T, leader: LeaderNode) = {
    val x: Dsl[LogCoords] = appendData(data, leader)
    val entries: Seq[AppendEntries[T]] = leader.makeAppend(data)
//    x.analyze(ae => insert(ae))
  }
}
