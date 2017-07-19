package miniraft.state

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

import agora.io.implicits._
import agora.api.worker.HostLocation
import agora.rest.RunningService
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.{Decoder, Encoder}
import miniraft.LeaderApi
import miniraft.state.RaftNode.async
import miniraft.state.rest.{LeaderRoutes, RaftRoutes, RaftSupportRoutes}

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * Pulls together the different components of a Raft System as seen by a raft node.
  *
  */
class RaftSystem[T: Encoder: Decoder] private (config: RaftConfig,
                                               asyncClient: async.RaftNodeActorClient[T],
                                               logic: RaftNodeLogic[T],
                                               protocol: ClusterProtocol,
                                               locationForId: NodeId => HostLocation,
                                               savedMessagesDir: Option[Path]) {

  val node: RaftNode[T] with LeaderApi[T] = asyncClient
  val leader: LeaderApi[T]                = asyncClient

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
  def routes(supportValueFromText: String => T): Route = {
    val all = raftRoutes.routes ~ leaderRoutes(supportValueFromText).routes

    if (config.includeRaftSupportRoutes) {
      all ~ supportRoutes.routes
    } else {
      all
    }
  }

  /** @param supportValueFromText the function used to create log entries of 'T' from user-specified text
    * @return /rest/raft/leader/... routes to act as an edge-node for client requests
    */
  def leaderRoutes(supportValueFromText: String => T) = {
    import config.serverImplicits._
    LeaderRoutes[T](node, leader, locationForId, supportValueFromText)
  }

  /** @return the /rest/raft/support/... routes for support/debugging/dev purposes
    */
  def supportRoutes = {
    import config.serverImplicits._
    RaftSupportRoutes(logic, protocol, savedMessagesDir)
  }

  /** @return The /rest/raft/vote and /rest/raft/append routes required to exist for this endpoint to participate w/ other cluster nodes
    */
  def raftRoutes: RaftRoutes[T] = {
    import config.serverImplicits._
    RaftRoutes[T](node)
  }

  /**
    * @param valueFromString a means to creates a T from the user's input field (for support routes only)
    * @return the runninsert service. eventually. probably.
    */
  def start(valueFromString: String => T = RaftSystem.commandFromJsonText): Future[RunningService[RaftConfig, RaftNode[T] with LeaderApi[T]]] = {

    import config.serverImplicits._

    /** kick off our election timer on startup */
    protocol.electionTimer.reset(None)

    val restRoutes = routes(valueFromString)
    val service    = RunningService.start(config, restRoutes, node)
    service.foreach(_.onShutdown {
      protocol.electionTimer.cancel()
      protocol.electionTimer.close()
      protocol.heartbeatTimer.cancel()
      protocol.heartbeatTimer.close()
      config.clientFor.close()
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
  def apply[T: Encoder: Decoder: ClassTag](config: RaftConfig)(applyToStateMachine: LogEntry[T] => Unit): Future[RaftSystem[T]] = {

    val nodeDirName = {
      config.id.map {
        case c if c.isLetterOrDigit => c
        case _                      => '_'
      }
    }

    val logDir = config.persistentDir.resolve(nodeDirName).mkDirs()

    def clusterNodeIds: Set[NodeId] = config.clusterNodes.keySet

    /**
      * Create our node, which needs a cluster protocol to be injected to do its work
      */
    val nodeId                  = config.id
    val logic: RaftNodeLogic[T] = RaftNodeLogic[T](nodeId, logDir)(applyToStateMachine)

    val node: async.RaftNodeActorClient[T] = {
      import config.serverImplicits._
      val (client, protocol) = RaftNode[T](logic, config.clusterNodes, config.election.timer, config.heartbeat.timer)
      client
    }

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
    val HostPort = "(.*):(\\d+)".r
    val locationForId: NodeId => HostLocation = {
      case nodeId @ HostPort(host, port) =>
        require(clusterNodeIds.contains(nodeId), s"$nodeId isn't a node in our cluster: ${clusterNodeIds}")
        HostLocation(host, port.toInt)
      case other => sys.error(s"Unrecognized node '$other' as a node in our cluster: ${clusterNodeIds}")
    }

    import config.serverImplicits.executionContext
    node.protocol().map { cluster =>
      /**
        * Phew! put all this together in a Raft System
        */
      new RaftSystem[T](config, node, logic, cluster, locationForId, saveDirAndCounterOpt.map(_._1))
    }
  }

}
