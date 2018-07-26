package riff.airc

import cats.InjectK
import cats.free.Free

// see https://underscore.io/blog/posts/2017/03/29/free-inject.html
case class LeaderAppendFree[C[_]](implicit inject: InjectK[LeaderAppend, C]) {

  private def asCop[T, A](req: LeaderAppend[T, A]): Free[C, A] = Free.liftInject[C](req)
    //: LeaderAppend[T, _] => Free[C, A]

  /** action to append data to a Raft leader node
    *
    * @param data
    * @tparam T
    * @return
    */
  def append[T](data: T): Free[C, AppendResponse] = {
    for {
      state <- getClusterState
      logState <- appendData(state.leader.term, data)
      responses <- makeAppends(state, logState, Array(data))
    } yield {
      responses
    }
  }

  type LeaderDsl[T] = Free[LeaderAppend, T]

  /**
    * This should append the data, uncommitted, to the log
    *
    * @param data
    * @tparam T
    * @return
    */
  def appendData[T](term : Int, data: T): Free[C, LogState] = asCop[T, LogState](AppendDataToLog[T](term, data))

  val getClusterState: Free[C, ClusterState] = asCop(GetClusterState)

  private def makeAppends[T](state: ClusterState, logState: LogState, data: Array[T]): Free[C, AppendResponse] = {
    if (state.leader.isLeader) {
      state.peers.toList match {
        case head :: tail =>
          val first = asCop[T, AppendResponse](SendAppendRequest(head, logState, data))
          tail.foldLeft(first) {
            case (free, node) =>
              free.flatMap { _ =>
                asCop[T, AppendResponse](SendAppendRequest(node, logState, data))
              }
          }
        case Nil => Free.pure[C, AppendResponse](SingleNodeCluster)
      }
    } else {
      Free.pure[C, AppendResponse](NotTheLeader(state.leader))
    }

  }

}
