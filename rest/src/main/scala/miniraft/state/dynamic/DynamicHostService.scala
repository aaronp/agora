package miniraft.state

import java.nio.file.Path

import agora.io.implicits._
import agora.api.worker.HostLocation
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import miniraft.RaftEndpoint

import scala.concurrent.Future

/**
  * Provides a handler [[onDynamicClusterMessage]] for Raft [[DynamicClusterMessage]] append messages
  *
  * @param config
  * @param clusterNodesFile
  */
case class DynamicHostService(config: RaftConfig, clusterNodesFile: Path) extends StrictLogging {

  import DynamicHostService._

  def onDynamicClusterMessage(msg: DynamicClusterMessage) = {
    msg match {
      case AddHost(newHost)         => addHost(newHost)
      case RemoveHost(hostToRemove) => removeHost(hostToRemove)
    }
  }

  def clusterNodes(): Map[NodeId, RaftEndpoint[DynamicClusterMessage]] = {
    val hosts                                                          = readNodes(clusterNodesFile).toList
    val initialNodes: Map[NodeId, RaftEndpoint[DynamicClusterMessage]] = config.clusterNodes[DynamicClusterMessage]
    val map = hosts.foldLeft(initialNodes) {
      case (map, host) =>
        val (id, endpoint) = entryForHost(host)
        map.updated(id, endpoint)
    }
    map
  }

  private[this] var raftSystem_ : RaftSystem[DynamicClusterMessage] = null

  private[state] def raftSystem: RaftSystem[DynamicClusterMessage] = {
    require(raftSystem_ != null)
    raftSystem_
  }

  private[state] def raftSystem_=(sys: RaftSystem[DynamicClusterMessage]) = {
    require(raftSystem_ == null, "already set")
    raftSystem_ = sys
    joinIfNecessary()
  }

  /**
    * Sets the started Raft system. If this is from a server not mentioned in the seed nodes,
    *
    * Then we should first send an 'AddHost' message to a node in the seed node list.
    *
    * On success, we can then start our raft system and join the cluster
    *
    * @return a future of a flag which determines if we can join (e.g. start)
    */
  private def joinIfNecessary() = {

    val weAreNewToThisCluster = !clusterNodes().contains(config.id)

    if (weAreNewToThisCluster) {
      // our cluster view is written to (directly) ... tell the rest of the cluster about us
      val msg    = AddHost(config.location)
      val leader = raftSystem.leaderClient()

      // clusterNodes
      // raftSystem.supportClient().state()

      // we aren't part of the cluster yet, so need towon't
      addHost(config.location)
      leader.append(msg)
    } else {
      Future.successful(true)
    }
  }

  private def removeHost(hostLocation: HostLocation) = {
    val id = miniraft.state.raftId(hostLocation)
    raftSystem.protocol.removeEndpoint(id)
  }

  private def addHost(newHost: HostLocation) = {
    val (id, endpoint) = entryForHost(newHost)
    val cluster        = raftSystem.protocol
    cluster.update(id, endpoint)
    persist()
  }

  private def entryForHost(hostLocation: HostLocation) = {
    val client   = config.clientConfig.clientFor(hostLocation)
    val endpoint = RaftEndpoint[DynamicClusterMessage](client)
    val id       = miniraft.state.raftId(hostLocation)
    (id, endpoint)
  }

  private def persist() = {
    val locations = raftSystem.protocol.clusterNodesById.keySet.collect {
      case HostLocation(location) => location
    }
    writeNodes(clusterNodesFile, locations)
  }
}

object DynamicHostService {

  def readNodes(clusterNodesFile: Path): Iterator[HostLocation] = {
    clusterNodesFile.lines.flatMap { line =>
      decode[HostLocation](line).right.toOption
    }
  }

  def writeNodes(clusterNodesFile: Path, nodes: Iterable[HostLocation]) = {
    clusterNodesFile.text = nodes.map(_.asJson.noSpaces).mkString("\n")
  }
}
