package miniraft.state

import java.nio.file.Path

import com.typesafe.config.{Config, ConfigFactory}
import io.circe.{Decoder, Encoder}
import agora.api.worker.HostLocation
import agora.rest.{ServerConfig, configForArgs}
import ConfigFactory.parseString
import akka.http.scaladsl.model.Uri
import miniraft.RaftEndpoint
import miniraft.state.rest.{LeaderClient, RaftSupportClient}

import scala.concurrent.duration._

class RaftConfig(c: Config) extends ServerConfig(c) {

  def withNodes(otherNodes: Iterable[HostLocation]): RaftConfig = {
    val otherNodesConfig = otherNodes.map { loc =>
      s"""{
         |  host : ${loc.host}
         |  port : ${loc.port}
         |}
      """.stripMargin
    }
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

  def seedNodeLocations: List[HostLocation] = seedNodeConfigs.map(hostLocationForConfig)

  def seedNodeConfigs: List[Config] = {
    import scala.collection.JavaConverters._
    config
      .getConfigList("seedNodes")
      .asScala
      .toList
  }

  def numberOfMessageToKeep = config.getInt("numberOfMessageToKeep")

  def messageRequestsDir: Option[Path] = {

    import agora.io.implicits._
    Option(config.getString("messageRequestsDir")).filterNot(d => d.isEmpty || d == "off" || numberOfMessageToKeep <= 0).map(_.asPath)
  }

  def clusterNodes[T: Encoder]: Map[NodeId, RaftEndpoint[T]] = {
    val nodes = seedNodeConfigs.map(c => newRaftClientById[T](c))
    nodes.toMap.ensuring(_.size == nodes.size, "duplicate seed nodes listed")
  }

  def newRaftClientById[T: Encoder](c: Config): (String, RaftEndpoint.Rest[T]) = {
    val loc        = hostLocationForConfig(c)
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

  def leaderClient[T: Encoder: Decoder]: LeaderClient[T] = leaderClientFor(location)

  def leaderClientFor[T: Encoder: Decoder](loc: HostLocation): LeaderClient[T] = LeaderClient[T](clientConfig.clientFor(loc), clientConfig.clientForUri)

  def supportClient[T: Encoder: Decoder]: RaftSupportClient[T] = supportClientFor(location)

  def supportClientFor[T: Encoder: Decoder](loc: HostLocation): RaftSupportClient[T] = new RaftSupportClient[T](clientConfig.clientFor(loc))

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
