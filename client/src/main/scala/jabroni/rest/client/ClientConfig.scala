package jabroni.rest.client

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.BaseConfig

class ClientConfig(override val config: Config) extends BaseConfig {

  val port = config.getInt("port")
  val host = config.getString("host")
}

object ClientConfig {
  def defaultConfig = ConfigFactory.load().getConfig("jabroni.client")

  def apply(conf: Config = defaultConfig): ClientConfig = new ClientConfig(conf)
}
