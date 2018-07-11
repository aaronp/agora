package riff

import riff.raft.{AppendEntries, RaftNode, RaftState}

trait RaftClient[T] {

  def append(data: T): RaftClient.AppendResponse
}


object RaftClient {

  sealed trait AppendResponse

  def apply[T](name: String, peers: Map[String, Endpoint], empty: T): RaftClient[T] = {
    val initialState = new RaftState[T](RaftNode(name), Nil, empty)


    initialState.update(RaftState.SendMessage(AppendEntries))
    ???
  }
}