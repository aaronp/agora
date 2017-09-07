package miniraft.state

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import agora.api.io.implicits._
import agora.api.worker.HostLocation
import agora.rest.RunningService
import agora.rest.client.RestClient
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}
import miniraft.state.RaftNode.async
import miniraft.state.rest.{LeaderClient, LeaderRoutes, RaftRoutes, RaftSupportRoutes}
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
                                                 savedMessagesDir: Option[Path]) {

  import config.serverImplicits._

  val node: RaftNode[T] with LeaderApi[T] = asyncClient
  val leader: LeaderApi[T]                = asyncClient

  def leaderClient(client : RestClient = config.clusterRestClient): LeaderClient[T] = config.leaderClientFor(client)

  def supportClient(client : RestClient = config.clusterRestClient) = config.supportClientFor(client)

  /** Produces Akka HTTP routes based on the given config, raft node and protocol.
    *
    * The 'supportValueFromText' is used when support routes are enabled to allow users to poke the client API
    * by making requests based on some text input (e.g. from an input field). It is not used when support routes are
    * disabled.
    *
    * If not provided, the default is to use the implicit json encoder.
    *
    * @param supportValueFromText a function which converts user input into a 'T', used in support routes for client requests
    * @return the akka http routes
    */
  def routes(supportValueFromText: String => T = RaftSystem.commandFromJsonText): Route = {
    val all = raftRoutes.routes ~ leaderRoutes.routes

    if (config.includeRaftSupportRoutes) {
      all ~ supportRoutes.routes
    } else {
      all
    }
  }

  /** @return /rest/raft/leader/... routes to act as an edge-node for client requests
    */
  def leaderRoutes = {
    LeaderRoutes[T](node, leader, locationForId)
  }

  /** @return the /rest/raft/support/... routes for support/debugging/dev purposes
    */
  def supportRoutes = {
    RaftSupportRoutes(logic, protocol, savedMessagesDir)
  }

  /** @return The /rest/raft/vote and /rest/raft/append routes required to exist for this endpoint to participate w/ other cluster nodes
    */
  def raftRoutes: RaftRoutes[T] = {
    RaftRoutes[T](node)
  }

  /**
    * @param valueFromString a means to creates a T from the user's input field (for support routes only)
    * @return the [[RunningService]] service. eventually. probably.
    */
  def start(valueFromString: String => T = RaftSystem.commandFromJsonText): Future[RunningService[RaftConfig, RaftNode[T] with LeaderApi[T]]] = {
    val restRoutes = routes(valueFromString)
    startWithRoutes(restRoutes)
  }

  def startWithRoutes(restRoutes: Route): Future[RunningService[RaftConfig, RaftNode[T] with LeaderApi[T]]] = {

    /** kick off our election timer on startup */
    protocol.electionTimer.reset(None)
    val service = RunningService.start(config, restRoutes, node)
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

  def commandFromJsonText[T: Decoder]: (String) => T = {
    val textAsParseResult = io.circe.parser.decode[T](_: String)
    textAsParseResult.andThen(_.right.get)
  }

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

  def apply[T: Encoder: Decoder: ClassTag](config: RaftConfig, initialNodes: Map[NodeId, RaftEndpoint[T]])(applyToStateMachine: LogEntry[T] => Unit): RaftSystem[T] = {

    /**
      * Create our node, which needs a cluster protocol to be injected to do its work
      */
    val nodeId                  = config.id
    val logic: RaftNodeLogic[T] = RaftNodeLogic[T](nodeId, config.logDir)(applyToStateMachine)

    import config.serverImplicits._
    val (node, nodeProtocol: async.ActorNodeProtocol[T]) = RaftNode[T](logic, initialNodes, config.election.timer, config.heartbeat.timer)

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
    new RaftSystem[T](config, node, logic, nodeProtocol, locationForId, saveDirAndCounterOpt.map(_._1))
  }

}
