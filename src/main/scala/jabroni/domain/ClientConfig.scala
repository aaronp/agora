package jabroni.domain

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.worker.HostLocation

class ClientConfig(config: Config = ClientConfig.defaultConfig()) {
  val host = config.getString("host")
  val port = config.getInt("port")
  val location = HostLocation(host, port)
}

object ClientConfig {
  def defaultConfig() = ConfigFactory.load().getConfig("jabroni.client")
  def apply() : ClientConfig = ClientConfig()
}
