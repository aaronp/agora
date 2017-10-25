package miniraft.state

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import agora.io.implicits._
import agora.api.worker.HostLocation
import agora.rest.RunningService
import agora.rest.client.RestClient
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}
import miniraft.state.RaftNode.async
import miniraft.state.rest._
import miniraft.{LeaderApi, RaftEndpoint}

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * Pulls together the different components of a Raft System as seen by a raft node.
  *
  */
class RaftSystem[T: Encoder: Decoder] protected (config: RaftConfig,
                                                 asyncClient: async.RaftNodeActorClient[T],
                                                 val logic: RaftNodeLogic[T],
                                                 val protocol: ClusterProtocol.BaseProtocol[T],
                                                 locationForId: NodeId => HostLocation,
                                                 saveDirAndCounterOpt: Option[(Path, AtomicInteger)]) {

  import config.serverImplicits._

  def leader = asyncClient

  def leaderClient(client: RestClient = config.clusterRestClient): LeaderClient[T] = config.leaderClientFor(client)

  def supportClient(client: RestClient = config.clusterRestClient) = config.supportClientFor(client)

  def defaultEndpoint: RaftEndpoint[T] = saveDirAndCounterOpt match {
    case None => asyncClient
    case Some((path, counter)) =>
      LoggingEndpoint[T](config.id, asyncClient, path, counter, config.numberOfMessageToKeep)
  }

  /** Produces Akka HTTP routes based on the given config, raft node and protocol.
    *
    * The 'supportValueFromText' is used when support routes are enabled to allow users to poke the client API
    * by making requests based on some text input (e.g. from an input field). It is not used when support routes are
    * disabled.
    *
    * If not provided, the default is to use the implicit json encoder.
    *
    * @return the akka http routes
    */
  def routes(endpoint: RaftEndpoint[T] = defaultEndpoint, leader: LeaderApi[T] = asyncClient): Route = {
    val all = raftRoutes(endpoint).routes ~ leaderRoutes(leader).routes

    if (config.includeRaftSupportRoutes) {
      all ~ supportRoutes.routes
    } else {
      all
    }
  }

  /** @return /rest/raft/leader/... routes to act as an edge-node for client requests
    */
  def leaderRoutes(leader: LeaderApi[T]) = {
    LeaderRoutes[T](leader, locationForId)
  }

  /** @return the /rest/raft/support/... routes for support/debugging/dev purposes
    */
  def supportRoutes = {
    RaftSupportRoutes(logic, protocol, saveDirAndCounterOpt.map(_._1))
  }

  /** @return The /rest/raft/vote and /rest/raft/append routes required to exist for this endpoint to participate w/ other cluster nodes
    */
  def raftRoutes(endpoint: RaftEndpoint[T]): RaftRoutes[T] = {
    RaftRoutes[T](endpoint)
  }

  /**
    * @return the [[RunningService]] service. eventually. probably.
    */
  def start(restRoutes: Route = routes()): Future[RunningService[RaftConfig, RaftSystem[T]]] = {

    /** kick off our election timer on startup */
    protocol.electionTimer.reset(None)
    val service = RunningService.start(config, restRoutes, this)
    service.foreach(_.onShutdown {
      protocol.electionTimer.close()
      protocol.heartbeatTimer.close()
      config.clientConfig.cachedClients.close()
    })
    service

  }
}

/**
  * Exposes functions for initialising raft systems from a configuration
  */
object RaftSystem extends StrictLogging {

  /**
    * Creates a raft node and [[ClusterProtocol]] given the raft config and 'applyToStateMachine' function.
    *
    * The applyToStateMachine is the function used to apply committed log entries of 'T' to whatever your use-case is.
    *
    * @param config              the configuration, duh!
    * @param applyToStateMachine a function to apply committed log entries to the state machine
    * @tparam T
    * @return the raft node for this system and a cluster protocol (the transport to use to access the rest of the cluster)
    */
  def apply[T: Encoder: Decoder: ClassTag](config: RaftConfig)(applyToStateMachine: LogEntry[T] => Unit): RaftSystem[T] = {
    apply[T](config, config.clusterNodes)(applyToStateMachine)
  }

  def apply[T: Encoder: Decoder: ClassTag](config: RaftConfig, initialNodes: Map[NodeId, RaftEndpoint[T]])(
      applyToStateMachine: LogEntry[T] => Unit): RaftSystem[T] = {

    /**
      * Create our node, which needs a cluster protocol to be injected to do its work
      */
    val nodeId                  = config.id
    val logic: RaftNodeLogic[T] = RaftNodeLogic[T](nodeId, config.logDir)(applyToStateMachine)

    import config.serverImplicits._
    val (node, nodeProtocol: async.ActorNodeProtocol[T]) =
      RaftNode[T](logic, initialNodes, config.election.timer, config.heartbeat.timer)

    /**
      * optionally set up a directory and unique id counters for tracking messages if we've turned on that sort of thing
      */
    val saveDirAndCounterOpt: Option[(Path, AtomicInteger)] = config.messageRequestsDir.map { saveDir =>
      val nodeDirName = nodeId.map {
        case c if c.isLetterOrDigit => c
        case _                      => '_'
      }
      val nodeLogDir             = saveDir.resolve(nodeDirName)
      val counter: AtomicInteger = new AtomicInteger(nodeLogDir.children.size)
      (nodeLogDir, counter)
    }

    /**
      * Provide a lookup used for redirecting to the leader based on a nodeId (which, in this system, is the host:port)
      */
    val locationForId: NodeId => HostLocation = {
      case nodeId @ HostLocation(location) =>
        val ids = nodeProtocol.clusterNodeIds
        require(ids.contains(nodeId), s"$nodeId isn't a node in our cluster: ${ids}")
        location
      case other => sys.error(s"Unrecognized node '$other' as a node in our cluster: ${nodeProtocol.clusterNodeIds}")
    }

    /**
      * Phew! put all this together in a Raft System
      */
    new RaftSystem[T](config, node, logic, nodeProtocol, locationForId, saveDirAndCounterOpt)
  }

}
