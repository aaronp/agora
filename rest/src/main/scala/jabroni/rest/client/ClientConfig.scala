package jabroni.rest.client

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.exchange.{Exchange, QueueObserver}
import jabroni.api.worker.HostLocation
import jabroni.rest.BaseConfig
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.worker.WorkerRoutes

class ClientConfig(override val config: Config) extends BaseConfig {

  val port = config.getInt("port")
  val host = config.getString("host")

  def location = HostLocation(host, port)

  def restClient = RestClient(this)
  def exchangeClient: Exchange with QueueObserver = {
    import implicits._
    ExchangeClient(restClient)
  }
  def workerRoutes: WorkerRoutes = {
    import implicits._
    WorkerRoutes(exchangeClient)
  }
}

object ClientConfig {
  def defaultConfig = ConfigFactory.load().getConfig("jabroni.client")

  def apply(conf: Config = defaultConfig): ClientConfig = new ClientConfig(conf)
}
