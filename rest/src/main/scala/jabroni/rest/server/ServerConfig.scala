package jabroni.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.BaseConfig

/**
  * A parsed configuration for our jabroni app
  */
class ServerConfig(override val config: Config) extends BaseConfig {

  val host = config.getString("host")
  val port = config.getInt("port")
  val launchBrowser = config.getBoolean("launchBrowser")
  val waitOnUserInput = config.getBoolean("waitOnUserInput")
}

object ServerConfig {
  def defaultConfig = ConfigFactory.load().getConfig("jabroni.server")

  def apply(conf: Config = defaultConfig): ServerConfig = new ServerConfig(conf)
}
