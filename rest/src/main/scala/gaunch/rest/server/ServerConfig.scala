package finance.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import finance.rest.BaseConfig

/**
  * A parsed configuration for our finance app
  */
class ServerConfig(override val config: Config) extends BaseConfig {

  val host = config.getString("host")
  val port = config.getInt("port")
  val launchBrowser = config.getBoolean("launchBrowser")
  val waitOnUserInput = config.getBoolean("waitOnUserInput")
}

object ServerConfig {
  def defaultConfig = ConfigFactory.load().getConfig("finance.server")

  def apply(conf: Config = defaultConfig): ServerConfig = new ServerConfig(conf)
}