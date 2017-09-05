package miniraft.state

import java.nio.file.Path

import agora.api.io.implicits._
import agora.api.worker.HostLocation
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import miniraft.RaftEndpoint

/**
  * Provides a handler [[onDynamicClusterMessage]] for Raft [[DynamicClusterMessage]] append messages
  * @param config
  * @param clusterNodesFile
  */
case class DynamicHostService(config: RaftConfig, clusterNodesFile: Path) {

  import DynamicHostService._

  def onDynamicClusterMessage(msg: DynamicClusterMessage) = {
    msg match {
      case AddHost(newHost) => addHost(newHost)
      case RemoveHost(hostToRemove) => removeHost(hostToRemove)
    }
  }
  def clusterNodes(): Map[NodeId, RaftEndpoint[DynamicClusterMessage]] = {
    val hosts: Iterator[HostLocation] = readNodes(clusterNodesFile)
    hosts.foldLeft(config.clusterNodes[DynamicClusterMessage]) {
      case (map, host) =>
        val (id, endpoint) = entryForHost(host)
        map.updated(id, endpoint)
    }
  }

  private[this] var raftSystem_ : RaftSystem[DynamicClusterMessage] = null

  private[state] def raftSystem: RaftSystem[DynamicClusterMessage] = {
    require(raftSystem_ != null)
    raftSystem_
  }

  private[state] def raftSystem_=(sys: RaftSystem[DynamicClusterMessage]): Unit = {
    require(raftSystem_ == null, "already set")
    raftSystem_ = sys
    readNodes(clusterNodesFile).foreach(addHost)
  }

  private def removeHost(hostLocation: HostLocation) = {
    val id = miniraft.state.raftId(hostLocation)
    raftSystem.protocol.removeEndpoint(id)
  }

  private def addHost(newHost: HostLocation) = {
    val (id, endpoint) = entryForHost(newHost)
    raftSystem.protocol.update(id, endpoint)
    persist()
  }

  private def entryForHost(hostLocation: HostLocation) = {
    val client = config.clientConfig.clientFor(hostLocation)
    val endpoint = RaftEndpoint[DynamicClusterMessage](client)
    val id = miniraft.state.raftId(hostLocation)
    (id, endpoint)
  }

  private def persist() = {
    val locations = raftSystem.protocol.clusterNodesById.keySet.collect {
      case AsLocation(location) => location
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
