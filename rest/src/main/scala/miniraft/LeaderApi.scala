package miniraft

import miniraft.state.{LogEntry, RaftNode}

import scala.concurrent.Future

trait LeaderApi[T] {

  def apply(command: T): Future[UpdateResponse]

}

object LeaderApi {
//
//  class Instance[T](raftNode: RaftNode[T]) extends LeaderApi[T] {
//
//    override def apply(command: T) = {
//      val index = raftNode.log.lastUnappliedIndex // + 1
//
//      val appendEntries: AppendEntries[T] = {
//        if (leaderState.isEmpty) {
//          throw new IllegalStateException(s"$id is no longer the leader. ${raftNode.persistentState.votedFor} is.")
//        }
//
//        val myEntry = LogEntry[T](currentTerm, index, command)
//        raftNode.log.append(myEntry)
//
//        mkAppendEntries(index, myEntry)
//      }
//
//      val clientAppendableResponse: UpdateResponse.Appendable = UpdateResponse(cluster.clusterNodeIds, index)
//
//      // when we reply to the client, then we can remove ourselves from the list
//      clientAppendableResponse.result.onComplete {
//        case res =>
//          AppendAckLock.synchronized {
//            logger.info(s"Removing client user ack $clientAppendableResponse on $res")
//            pendingAppendAcks = pendingAppendAcks diff (List(clientAppendableResponse))
//          }
//      }
//
//      AppendAckLock.synchronized {
//        pendingAppendAcks = clientAppendableResponse :: pendingAppendAcks
//      }
//
//      cluster.tellOthers(appendEntries)
//
//      clientAppendableResponse
//    }
//  }

}
