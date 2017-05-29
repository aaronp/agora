package miniraft.state

import java.nio.file.Path

import akka.actor.ActorSystem
import jabroni.rest.test.TestTimer
import miniraft.state.Log.Formatter
import miniraft.state.RaftEndpoint.buffered

object TestCluster {

  implicit val system = ActorSystem("test-cluster")
  implicit val ec = system.dispatcher

  import jabroni.domain.io.implicits._

  case class under(dir: Path) {
    def of[T](firstNode: String, theRest: String*)(implicit fmt: Formatter[T, Array[Byte]]): Map[String, (RaftNode[T], RaftEndpoint[T])] = {

      val nodesById = (theRest.toSet + firstNode).map { id =>
        id -> RaftNode[T](id, dir.resolve(s"node-$id").mkDirs())
      }.toMap


      val endpointsById: Map[NodeId, buffered.BufferedClient[T]] = nodesById.mapValues(_ => RaftEndpoint.deferred[T])

      val initialized = nodesById.map {
        case (id, node: RaftNode[T]) =>
          val cluster = ClusterProtocol(node, endpointsById, new TestTimer(), new TestTimer())
          val e = node.endpoint(cluster)
          endpointsById(id).setEndpoint(e)
          id -> (node, e)
      }

      initialized
    }
  }

}
