package miniraft.state

import java.nio.file.Path

import akka.actor.ActorSystem
import agora.rest.test.{BufferedTransport, TestTimer}
import miniraft.state.Log.Formatter
import miniraft.state.RaftNode.async
import miniraft.{LeaderApi, RaftEndpoint, UpdateResponse, state}

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

object TestCluster {

  implicit val system = ActorSystem("test-cluster")
  implicit val ec     = system.dispatcher

  case class TestClusterNode[T](logic: RaftNodeLogic[T], asyncNode: async.RaftNodeActorClient[T], protocol: BufferedTransport) {

    def append(value: T)(implicit ec: ExecutionContext): UpdateResponse = {
      val res: UpdateResponse.Appendable = logic.add(value, protocol)
      res
    }

    def id = logic.id

    def node: RaftNode[T] = asyncNode

    def leaderId = logic.leaderId

    def endpoint: RaftEndpoint[T] = node

    def lastUnappliedIndex = logic.lastUnappliedIndex

    def leader: LeaderApi[T] = asyncNode

    override def toString = logic.toString
  }

  case class under(dir: Path) {

    import agora.api.io.implicits._
    def of[T: ClassTag](firstNode: String, theRest: String*)(applyToStateMachine: (NodeId, LogEntry[T]) => Unit)(
        implicit fmt: Formatter[T, Array[Byte]]): Map[NodeId, async.RaftNodeActorClient[T]] = {
      instance(theRest.toSet + firstNode) { id =>
        val nodeDir = dir.resolve(s"node-$id").mkDirs()

        PersistentState[T](nodeDir)(applyToStateMachine(id, _))
      }
    }
  }

  def instance[T: ClassTag](ids: Set[NodeId])(newPersistentState: NodeId => PersistentState[T])(implicit fmt: Formatter[T, Array[Byte]]) = {

    val nodesAndProtocols = ids.map { (id: String) =>
      val logic = RaftNodeLogic[T](id, newPersistentState(id))

      RaftNode(logic, Map.empty, new TestTimer(), new TestTimer())
    }

    val testNodeById: Map[NodeId, async.RaftNodeActorClient[T]] = nodesAndProtocols.map {
      case (n, p) => p.ourNodeId -> n
    }.toMap

    nodesAndProtocols.map(_._2).foreach(_.setEndpoints(testNodeById))

    testNodeById
  }

  def nodeForLogic[T: ClassTag](logic: RaftNodeLogic[T], protocol: BufferedTransport) = {
    val endpoint: async.RaftNodeActorClient[T] = RaftNode[T](logic, protocol)
    TestClusterNode(logic, endpoint, protocol)
  }

  def newNode[T: ClassTag](id: NodeId, protocol: BufferedTransport)(newPersistentState: NodeId => PersistentState[T])(implicit fmt: Formatter[T, Array[Byte]]) = {
    val logic = RaftNodeLogic[T](id, newPersistentState(id))
    nodeForLogic(logic, protocol)
  }

  def nodeForState[T: ClassTag](nodeId: NodeId, initialState: RaftState[T], protocol: BufferedTransport) = {
    val logic = RaftNodeLogic(nodeId, initialState)
    nodeForLogic(logic, protocol)
  }

}
