package riff.airc

import cats.free.Free
import cats.{Inject, InjectK}
import riff.raft.Peer

// see https://underscore.io/blog/posts/2017/03/29/free-inject.html
class LeaderAppendFree[C[_]](implicit inject: InjectK[LeaderAppend, C]) {

  private val asCop = Free.liftInject[C]

  /** action to append data to a Raft leader node
    *
    * @param data
    * @tparam T
    * @return
    */
  def append[T](data: T): Free[C, AppendResponse] = {
    for {
      logState <- appendData(data)
      peers <- getClusterNodes
      responses <- makeAppends(peers, logState, Array(data))
    } yield {
      responses
    }
  }

  type LeaderDsl[T] = Free[LeaderAppend, T]

  /**
    * This should append the data, uncommitted, to the log
    * @param data
    * @tparam T
    * @return
    */
  def appendData[T](data: T): Free[C, LogState] = asCop[LeaderAppend, LogState](AppendDataToLog[T](data))

  val getClusterNodes: Free[C, Set[Peer]] = asCop(GetClusterNodes)

  private def makeAppends[T](nodes: Set[Peer], logState: LogState, data: Array[T]): Free[C, AppendResponse] = {
    nodes.toList match {
      case head :: tail =>
        val first = asCop[LeaderAppend, AppendResponse](SendAppendRequest(head, logState, data))
        tail.foldLeft(first) {
          case (free, node) =>
            free.flatMap { _ =>
              asCop[LeaderAppend, AppendResponse](SendAppendRequest(node, logState, data))
            }
        }
      case Nil =>
        asCop[LeaderAppend, AppendResponse](CommitLog(logState))
    }
  }

}
