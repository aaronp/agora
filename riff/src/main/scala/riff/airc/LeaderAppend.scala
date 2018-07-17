package riff.airc

import cats.free.Free

sealed trait LeaderAppend[A]

object LeaderAppend {

  def leaderAppend[T](data: T): Free[LeaderAppend, AppendResponse] = {
    for {
      logState <- appendData(data)
      nodes <- getClusterNodes
      responses <- makeAppends(nodes, logState, data)
    } yield {
      responses
    }
  }


  def appendData[T](data: T): Free[LeaderAppend, LogState] = Free.liftF[LeaderAppend, LogState](AppendDataToLog[T](data))

  val getClusterNodes: Free[LeaderAppend, Set[String]] = Free.liftF(GetClusterNodes)

  private def makeAppends[T](nodes: Set[String], logState: LogState, data: T): Free[LeaderAppend, AppendResponse] = {
    nodes.toList match {
      case head :: tail =>
        val first = Free.liftF[LeaderAppend, AppendResponse](SendAppendRequest(head, logState, data))
        tail.foldLeft(first) {
          case (free, node) =>
            val q = free.flatMap { _ =>
              Free.liftF[LeaderAppend, AppendResponse](SendAppendRequest(node, logState, data))
            }
            q
        }
      case Nil => Free.liftF[LeaderAppend, AppendResponse](CommitLog(logState))
    }

  }

}


case class LogState(term: Int, index: Int)

case class AppendDataToLog[T](data: T) extends LeaderAppend[LogState]

case class AppendResponse(term: Int, matchIndex: Int, success : Boolean)
case class SendAppendRequest[T](to: String, logState: LogState, data: T) extends LeaderAppend[AppendResponse]

case object GetClusterNodes extends LeaderAppend[Set[String]]

case class CommitLog(logState: LogState) extends LeaderAppend[AppendResponse]

