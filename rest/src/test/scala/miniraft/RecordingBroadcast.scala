package miniraft

import scala.concurrent.{Future, Promise}


case class RecordingBroadcast(underlying: Broadcast) extends Broadcast {
  private var pending = List[(RaftRequest, Promise[RaftResponse])]()

  def pendingRequests = pending

  def filter(f: RaftRequest => Boolean): List[(RaftRequest, Promise[RaftResponse])] = {
    val found = pending.filter {
      case (r, p) => f(r)
    }
    pending = pending.filterNot {
      case (r, p) => f(r)
    }
    found
  }

  def remove(r: RaftRequest) = filter(_ == r)

  override def nodeIds: Set[NodeId] = underlying.nodeIds

  def justSend(msg: RaftRequest): Future[RaftResponse] = synchronized {
    msg match {
      case req: AppendEntries[_] => underlying.onAppendEntries(req)
      case req: RequestVote => underlying.onRequestVote(req)
    }

  }

  def flushAll: List[Future[RaftResponse]] = {
    val r = pending.map(flush)
    pending = Nil
    r
  }

  def flush(pear: (RaftRequest, Promise[RaftResponse])): Future[RaftResponse] = synchronized {
    val (r, p) = pear
    val resp = justSend(r)
    p.completeWith(resp)
    resp
  }

  override def handle(msg: RaftRequest): Future[RaftResponse] = synchronized {
    val promise = Promise[RaftResponse]()
    pending = (msg, promise) :: pending
    promise.future
  }
}
