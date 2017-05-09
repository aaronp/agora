package miniraft

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.worker.HostLocation
import jabroni.rest.{RunningService, ServerConfig, configForArgs}
import jabroni.rest.ui.UIRoutes
import jabroni.rest._

object RaftConfig {
  def baseConfig = ConfigFactory.load("raft.conf")

  def defaultConfig = baseConfig.resolve

  def apply(firstArg: String, theRest: String*): RaftConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, defaultConfig: Config = defaultConfig): RaftConfig = {
    RaftConfig(configForArgs(args, defaultConfig))
  }

  type RunningRaft = RunningService[RaftConfig, RaftRoutes[NodeState]]
}

case class RaftConfig(override val config: Config) extends ServerConfig {
  override type Me = RaftConfig

  override def self: Me = this

  def startRaft() = runWithRoutes("Raft", routes, raftRoutes)

  def seedNodes: Set[HostLocation] = {
    import scala.collection.JavaConverters._
    config.getConfigList("seedNodes").asScala.map(asHostLocation).toSet
  }

  val raftId = config.getString("raftId")

  def broadcast: Broadcast = {
    new RestBroadcast(Map.empty)
  }

  lazy val raftNode = RaftNode(raftId)
  lazy val raftRoutes: RaftRoutes[NodeState] = {
    import io.circe.generic.auto._
    import implicits._
    RaftRoutes[NodeState](raftNode)
  }

  def routes: Route = {
    if (includeUIRoutes) {
      val uiRoutes: Route = UIRoutes("ui/index.html").routes
      raftRoutes.routes ~ uiRoutes
    } else {
      raftRoutes.routes
    }
  }
}
