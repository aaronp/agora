package riff.airc

import cats.arrow.FunctionK
import cats.data.Writer
import cats.free.Free
import cats.implicits._
import riff.RiffSpec

class LeaderAppendTest extends RiffSpec {

  type W[T] = Writer[String, T]

  implicit object TestInterpreter extends FunctionK[LeaderAppend, W] {
    override def apply[A](fa: LeaderAppend[A]): W[A] = {
      fa match {
        case GetClusterNodes => Writer("Getting cluster state", Set("a", "second", "charlies").asInstanceOf[A])
        case AppendDataToLog(data) =>
          val logState = LogState(123, 456)
          Writer(s"Saving $data", logState.asInstanceOf[A])
        case SendAppendRequest(to, log, data: A) =>
          val resp = AppendResponse(log.term, to.hashCode, true)
          Writer(s"Sending $to @ $log $data", resp.asInstanceOf[A])
        case CommitLog(log) =>
          val resp = AppendResponse(log.term, log.index, true)
          Writer(s"Committing $log", resp.asInstanceOf[A])
      }
    }
  }

  "LeaderAppend" should {
    "send requests to all nodes with the same match index" in {
      val program: Free[LeaderAppend, AppendResponse] = LeaderAppend.leaderAppend("foo")

      val res: W[AppendResponse] = program.foldMap(TestInterpreter)

      val (desc, resp) = res.run
      println(desc)
      println(resp)

    }
  }
}
