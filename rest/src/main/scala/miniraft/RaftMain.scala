package miniraft

import jabroni.rest.RunningService

import scala.concurrent.Future

class RaftMain {

  def main(args: Array[String]): Unit = {
    val future: Future[RunningService[RaftConfig, RaftRoutes[NodeState]]] = RaftConfig(args).startRaft()
  }
}
