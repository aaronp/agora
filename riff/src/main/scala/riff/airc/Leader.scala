package riff.airc

import agora.io.ToBytes
import cats.free.Free
import cats.~>
import riff.impl.RaftLog

import scala.util.{Success, Try}

class Leader[T : ToBytes] {

  case class LocalState(clusterState : ClusterState, log : RaftLog[T], send : SendAppendRequest[T] => NodeResponse)

  type LeaderAppendT[A] = LeaderAppend[T, A]

  class TryLeader(state : LocalState) extends (LeaderAppendT ~> Try) {
    override def apply[A](fa: LeaderAppendT[A]): Try[A] = {
      fa match {
        case GetClusterState => Success(state.clusterState).asInstanceOf[Try[A]]
        case AppendDataToLog(term, data : T) =>
          Try(state.log.append(term, data)).asInstanceOf[Try[A]]
        case req @ SendAppendRequest(to, logState, entries : Array[T]) =>
          Try(state.send(req)).asInstanceOf[Try[A]]
      }
    }
  }

  def append(data : T): Free[LeaderAppend[T, _], AppendResponse] = {
    LeaderAppendFree[LeaderAppend[T, _]].append(data)
  }

  def tryAppend(state : LocalState, data : T): Try[AppendResponse] = append(data).foldMap(new TryLeader(state))

}
