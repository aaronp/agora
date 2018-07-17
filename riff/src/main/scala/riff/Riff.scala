package riff

import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import org.reactivestreams.Publisher
import riff.impl.RiffInstance
import riff.raft.{CommitLog, IsEmpty, RaftState}

import scala.concurrent.duration.FiniteDuration

/**
  * The external use-cases for a node in the Raft protocol are:
  *
  * 1) adding/removing nodes
  * 2) appending data to the cluster, which should return either a new (pending) commit coords, a redirect, or a 'no known leader'
  * 3) subscribing to lifecycle events, which should return a Publisher which lets subscribers know about cluster and leadership changes
  * 4) subscribing to a commit log, which should return a Publisher which lets subscribers know when logs are appended or committed
  *
  * All of these algebras don't necessarily have to be in the same trait
  */


// https://softwaremill.com/free-tagless-compared-how-not-to-commit-to-monad-too-early/
trait DataSync[A] {
  def append(data: A): Publisher[AppendResult]
}

/**
  * Represents the ability to add and remove members from the cluster dynamically.
  *
  * Clusters should ideally be static, so this functionality is purely optional.
  *
  * The implied behaviour is that the implementation will know how to implement [[RaftClusterClient]]
  */
trait ClusterAdmin {
  def addNode(name: String): Boolean

  def removeNode(name: String): Boolean
}

trait ClusterNode {
  def membership: Publisher[ClusterEvent]

  /**
    * Invoked periodically when a node becomes leader
    */
  def onSendHeartbeatTimeout()

  /**
    * Invoked periodically when a node becomes a follower
    */
  def onReceiveHeartbeatTimeout()
}

trait RaftTimer {

  type C

  /**
    * Resets the heartbeat timeout for the given node.
    *
    * It contract is assumed that this function will be called periodically from the node passed in,
    * and it is up to the implementation to invoking 'oElectionTimeout' should it not be invoked
    * within a certain time
    *
    * @param node
    */
  def resetReceiveHeartbeatTimeout(node: ClusterNode, previous: Option[C]): C

  def resetSendHeartbeatTimeout(node: ClusterNode, previous: Option[C]): C

  def cancelTimeout(c: C)

}

object RaftTimer {
  def apply(sendHeartbeatTimeout: FiniteDuration, receiveHeartbeatTimeout: FiniteDuration)(implicit sched: Scheduler): RaftTimer = {
    new RaftTimer {
      type C = Cancelable

      override def cancelTimeout(c: Cancelable): Unit = c.cancel()


      override def resetSendHeartbeatTimeout(node: ClusterNode, previous: Option[C]) = {
        previous.foreach(_.cancel())
        val cancel: Cancelable = sched.scheduleOnce(sendHeartbeatTimeout) {
          node.onSendHeartbeatTimeout()
        }
        cancel
      }

      override def resetReceiveHeartbeatTimeout(node: ClusterNode, previous: Option[Cancelable]): Cancelable = {
        previous.foreach(_.cancel())
        val cancel: Cancelable = sched.scheduleOnce(receiveHeartbeatTimeout) {
          node.onReceiveHeartbeatTimeout()
        }
        cancel
      }
    }
  }
}


trait Riff[A] extends DataSync[A] with ClusterAdmin with ClusterNode

object Riff {

  def apply[A: IsEmpty](initial: RaftState[A],
                        send: RaftClusterClient[Observable],
                        log: CommitLog[A],
                        timer : RaftTimer)(implicit sched: Scheduler): Riff[A] = {
    new RiffInstance(initial, send, log, timer)
  }
}