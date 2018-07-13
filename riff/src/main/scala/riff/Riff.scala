package riff

import monix.execution.Scheduler
import monix.reactive.Observable
import org.reactivestreams.Publisher
import riff.impl.RiffInstance
import riff.raft.{IsEmpty, RaftState}

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

trait ClusterAdmin {
  def addNode(name: String): Boolean

  def removeNode(name: String): Boolean
}

trait ClusterNode {
  def membership: Publisher[ClusterEvent]
}


trait Riff[A] extends DataSync[A] with ClusterAdmin with ClusterNode

object Riff {

  def apply[A: IsEmpty](initial: RaftState[A],
                        send: Send[Observable],
                        log: CommitLog[A])(implicit sched: Scheduler): Riff[A] = {
    new RiffInstance(initial, send, log)
  }
}