package miniraft.state

import java.nio.file.Path

import akka.actor.ActorSystem
import agora.rest.test.TestTimer
import miniraft.state.Log.Formatter
import miniraft.state.RaftNode.async
import miniraft.{LeaderApi, RaftEndpoint, state}

import scala.reflect.ClassTag

object TestCluster {

  implicit val system = ActorSystem("test-cluster")
  implicit val ec     = system.dispatcher

  import agora.domain.io.implicits._

  case class TestClusterNode[T](logic: RaftNodeLogic[T], asyncNode: async.RaftNodeActorClient[T], protocol: state.ClusterProtocol.Buffer[T]) {
    def id = logic.id

    def node: RaftNode[T] = asyncNode

    def leaderId = logic.leaderId

    def endpoint: RaftEndpoint[T] = node

    def lastUnappliedIndex = logic.lastUnappliedIndex

    def leader: LeaderApi[T] = asyncNode
  }

  case class under(dir: Path) {
    def of[T: ClassTag](firstNode: String, theRest: String*)(applyToStateMachine: LogEntry[T] => Unit)(implicit fmt: Formatter[T, Array[Byte]]): Map[String, TestClusterNode[T]] = {
      apply(theRest.toSet + firstNode) { id =>
        val nodeDir = dir.resolve(s"node-$id").mkDirs()

        PersistentState[T](nodeDir)(applyToStateMachine)
      }
    }
  }

  def apply[T: ClassTag](ids: Set[NodeId])(newPersistentState: NodeId => PersistentState[T])(implicit fmt: Formatter[T, Array[Byte]]): Map[String, TestClusterNode[T]] = {

    val testNodeById = ids.map { (id: String) =>
      val tcn = newNode(id)(newPersistentState)
      id -> tcn
    }.toMap

    val endpointsById: Map[String, RaftEndpoint[T]] = testNodeById.mapValues(_.endpoint)
    testNodeById.values.foreach(_.protocol.update(endpointsById))

    testNodeById
  }

  def nodeForLogic[T: ClassTag](logic: RaftNodeLogic[T]) = {
    val protocol                               = new state.ClusterProtocol.Buffer[T](logic.id, new TestTimer(), new TestTimer(), Map.empty)
    val endpoint: async.RaftNodeActorClient[T] = RaftNode[T](logic, protocol)
    protocol.updateHander {
      case (from, _, _, resp) => endpoint.onResponse(from, resp)
    }
    TestClusterNode(logic, endpoint, protocol)
  }

  def newNode[T: ClassTag](id: NodeId)(newPersistentState: NodeId => PersistentState[T])(implicit fmt: Formatter[T, Array[Byte]]) = {
    val logic = RaftNodeLogic[T](id, newPersistentState(id))
    nodeForLogic(logic)
  }

  def nodeForState[T: ClassTag](nodeId: NodeId, initialState: RaftState[T]) = {
    val logic = RaftNodeLogic(nodeId, initialState)
    nodeForLogic(logic)
  }

}
