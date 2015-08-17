package finance.rest.client

import com.typesafe.config.{Config, ConfigFactory}
import finance.rest.BaseConfig

class ClientConfig(override val config: Config) extends BaseConfig {

  val port = config.getInt("port")
  val host = config.getString("host")
}

object ClientConfig {
  def defaultConfig = ConfigFactory.load().getConfig("finance.client")

  def apply(conf: Config = defaultConfig): ClientConfig = new ClientConfig(conf)
}