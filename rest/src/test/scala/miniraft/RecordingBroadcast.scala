package miniraft

import io.circe.{Decoder, Encoder}

import scala.concurrent.{Future, Promise}


case class RecordingBroadcast(underlying: Broadcast) extends Broadcast {
//  private var pendingAppendEntries = List[(RaftRequest, Promise[AppendEntriesResponse])]()
//  private var pendingRequestVote = List[(RaftRequest, Promise[RequestVoteResponse])]()
//
//  def pendingRequests = {
//    val list = pendingAppendEntries ++ pendingRequestVote
//    list.foldLeft(List[(RaftRequest, Promise[RaftResponse])]()) {
//      case (builder, (req, resp: Promise[RaftResponse])) => (req, resp) :: builder
//    }
//  }
//
//  //
//  //  def filter(f: RaftRequest => Boolean): List[(RaftRequest, Promise[RaftResponse])] = {
//  //    val found = pending.filter {
//  //      case (r, p) => f(r)
//  //    }
//  //    pending = pending.filterNot {
//  //      case (r, p) => f(r)
//  //    }
//  //    found
//  //  }
//  //
//  //  def remove(r: RaftRequest) = filter(_ == r)
//
//  override def nodeIds: Set[NodeId] = underlying.nodeIds
//
//  def justSend(msg: RaftRequest): Future[RaftResponse] = synchronized {
//    msg match {
//      case req: AppendEntries[_] => underlying.onAppendEntries(req)
//      case req: RequestVote => underlying.onRequestVote(req)
//    }
//  }
//
//  def flushAll: List[Future[RaftResponse]] = {
//    val r = pendingRequests.map(flush)
//    pendingAppendEntries = Nil
//    pendingRequestVote = Nil
//    r
//  }
//
//  //
//  def flush(pear: (RaftRequest, Promise[RaftResponse])): Future[RaftResponse] = synchronized {
//    val (r, p) = pear
//    val resp = justSend(r)
//    p.completeWith(resp)
//    resp
//  }

  //
  //
  //  def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = {
  //        val promise = Promise[AppendEntriesResponse]()
  //        pending = (req, promise) :: pending
  //        promise.future
  //  }
  //
  //  def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = {
  //    val promise = Promise[RequestVoteResponse]()
  //    pending = (req, promise) :: pending
  //    promise.future
  //  }

  //
  //  override def handle(msg: RaftRequest): Future[RaftResponse] = synchronized {
  //    val promise = Promise[RaftResponse]()
  //    pending = (msg, promise) :: pending
  //    promise.future
  //  }
  override def onAppendEntries[T: Encoder : Decoder](req: AppendEntries[T]): Future[AppendEntriesResponse] = ???

  override def onRequestVote(req: RequestVote): Future[RequestVoteResponse] = ???

  override def nodeIds: Set[NodeId] = ???
}
