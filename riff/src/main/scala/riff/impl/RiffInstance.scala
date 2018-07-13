package riff.impl

import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.execution.atomic.{Atomic, AtomicAny}
import monix.reactive.{Observable, Pipe}
import org.reactivestreams.Publisher
import riff._
import riff.raft.RaftState._
import riff.raft._

class RiffInstance[A: IsEmpty, T](initial: RaftState[A], send: Send[Observable], commitLog: CommitLog[A])(implicit sched: Scheduler) extends Riff[A] with StrictLogging {
  private val stateRef: AtomicAny[RaftState[A]] = Atomic(initial)

  private val (membershipObserver, membershipObservable) = Pipe.publish[ClusterEvent].multicast
  //private val (appendObserver, appendObservable) = Pipe.publish[AppendResult].multicast

  override def membership: Publisher[ClusterEvent] = {
    stateRef.transformAndExtract[Publisher[ClusterEvent]] { state =>
      val adds = Observable(state.node.clusterNodes.map(ClusterEvent.nodeAdded))
      val all = adds ++ membershipObservable
      all.toReactivePublisher[ClusterEvent] -> state
    }
  }

  override def append(data: A): Publisher[AppendResult] = {
    val (logCoordOps, clusterSize, entries: Observable[AppendEntries[A]]) = stateRef.transformAndExtract { state =>
      state.asLeader match {
        case None =>
          val pear = (Option.empty[LogCoords], state.node.clusterSize, Observable.raiseError(state.node.leaderOpinion.asError))
          pear -> state
        case Some(leader) =>
          val entries: Seq[AppendEntries[A]] = leader.makeAppend(data)
          val pear = (Option(leader.logCoords), leader.clusterSize, Observable.fromIterable(entries))
          pear -> state
      }
    }

    val replies: Observable[AppendEntriesReply] = entries.concatMapDelayErrors { entry =>
      send(entry)
    }

    logCoordOps match {
      case None =>
        entries.toReactivePublisher[AppendResult]
      case Some(logCoords) =>
        val appendResults: Observable[AppendResult] = replies.foldLeftF(AppendResult(logCoords, Map.empty, clusterSize)) {
          case (aer, reply) =>
            update(reply)
            aer.copy(appended = aer.appended.updated(reply.from, reply.success))
        }
        appendResults.toReactivePublisher[AppendResult]
    }

  }

  def trigger(action: RaftState.ActionResult) = {
    logger.debug(s"trigger($action)")
    action match {
      case LogMessage(explanation, true) => logger.warn(explanation)
      case LogMessage(explanation, false) => logger.info(explanation)
      case SendMessage(msg) => send(msg).foreach(update)
      case AppendLogEntry(coords, data: A) => commitLog.append(coords, data)
      case CommitLogEntry(coords) => commitLog.commit(coords)
      case ResetSendHeartbeatTimeout =>
      case ResetElectionTimeout =>
    }
  }

  private def update(msg: RaftMessage) = {
    logger.debug(s"update($msg)")
    stateRef.transform { state =>
      val (newState, action) = state.onMessage(msg)
      action.foreach(trigger)
      newState
    }
  }

  override def addNode(name: String) = {
    stateRef.transformAndExtract[Boolean] { state =>
      state.addNode(Peer(name)).fold(false -> state) { newState =>
        membershipObserver.onNext(ClusterEvent.nodeAdded(name))
        true -> newState
      }
    }
  }

  override def removeNode(name: String) = {
    stateRef.transformAndExtract[Boolean] { state =>
      state.removeNode(name).fold(false -> state) {
        newState =>
          membershipObserver.onNext(ClusterEvent.nodeRemoved(name))
          true -> newState
      }
    }
  }
}