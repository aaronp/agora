package miniraft.state

import java.nio.file.Path

import agora.api.worker.HostLocation
import agora.config.configForArgs
import agora.rest.ServerConfig
import agora.rest.client.RestClient
import com.typesafe.config.ConfigFactory.parseString
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.{Decoder, Encoder}
import miniraft.RaftEndpoint
import miniraft.state.rest.{LeaderClient, RaftSupportClient}

import scala.concurrent.duration._

class RaftConfig(c: Config) extends ServerConfig(c) {

  def withNodes(otherNodes: Iterable[HostLocation]): RaftConfig = {
    val otherNodesConfig = otherNodes.map(_.asHostPort).map(_.quoted)
    withOverrides(parseString(otherNodesConfig.mkString("seedNodes : [", ",", "]")))
  }

  def withLocation(location: HostLocation): RaftConfig = {
    withOverrides(newConfig(Map("host" -> location.host, "port" -> location.port.toString)))
  }

  override def withFallback(fallback: Config): RaftConfig = new RaftConfig(config.withFallback(fallback))

  override def withOverrides(overrides: Config): RaftConfig = new RaftConfig(overrides).withFallback(config)

  def id: String = raftId(location)

  def persistentDir: Path = {

    import agora.io.implicits._

    val createPersistentDirWhenAbsent = config.getBoolean("createPersistentDirWhenAbsent")
    val dir                           = config.getString("persistentDir").asPath
    if (createPersistentDirWhenAbsent) {
      dir.mkDirs()
    } else {
      dir
    }
  }

  def nodeDirName = {
    id.map {
      case c if c.isLetterOrDigit => c
      case _                      => '_'
    }
  }

  def logDir = {
    import agora.io.implicits._
    persistentDir.resolve(nodeDirName).mkDirs()
  }

  def seedNodeLocations: List[HostLocation] = {
    config.asList("seedNodes").map {
      case HostLocation(loc) => loc
      case other             => sys.error(s"Couldn't parse $other as a <host>:<port>")
    }
  }

  def clusterRestClient = {
    val list = seedNodeLocations
    clientConfig.clientFor(list)
  }

  def numberOfMessageToKeep = config.getInt("numberOfMessageToKeep")

  def messageRequestsDir: Option[Path] = {

    import agora.io.implicits._
    Option(config.getString("messageRequestsDir"))
      .filterNot(d => d.isEmpty || d == "off" || numberOfMessageToKeep <= 0)
      .map(_.asPath)
  }

  def clusterNodes[T: Encoder]: Map[NodeId, RaftEndpoint[T]] = {
    val nodes = seedNodeLocations.map(c => newRaftClientById[T](c))
    nodes.toMap.ensuring(_.size == nodes.size, "duplicate seed nodes listed")
  }

  def newRaftClientById[T: Encoder](loc: HostLocation): (String, RaftEndpoint.Rest[T]) = {
    val restClient = clientConfig.clientFor(loc)
    val endpoint   = RaftEndpoint[T](restClient)
    raftId(loc) -> endpoint
  }

  class TimerConfig(name: String) {
    private val hb = config.getConfig(name)
    val min        = hb.getDuration("min").toMillis.millis
    val max        = hb.getDuration("max").toMillis.millis

    def timer = InitialisableTimer(name, min, max)(serverImplicits.system)
  }

  def leaderClient[T: Encoder: Decoder]: LeaderClient[T] = leaderClientFor(clientConfig.clientFor(location))

  def leaderClientFor[T: Encoder: Decoder](client: RestClient): LeaderClient[T] =
    LeaderClient[T](client, clientConfig.clientForUri)

  def supportClient[T: Encoder: Decoder]: RaftSupportClient[T] = supportClientFor(clientConfig.clientFor(location))

  def supportClientFor[T: Encoder: Decoder](client: RestClient): RaftSupportClient[T] =
    new RaftSupportClient[T](client)

  def heartbeat = new TimerConfig("heartbeat")

  def election = new TimerConfig("election")

  def includeRaftSupportRoutes = config.getBoolean("includeRaftSupportRoutes")
}

object RaftConfig {

  def apply(firstArg: String, theRest: String*): RaftConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.empty): RaftConfig = {
    load().withOverrides(configForArgs(args, fallbackConfig))
  }

  def load(): RaftConfig = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config): RaftConfig = apply(config.getConfig("raft").ensuring(!_.isEmpty))

  def apply(config: Config): RaftConfig = new RaftConfig(config)

  def unapply(config: RaftConfig) = Option(config.config)

}
