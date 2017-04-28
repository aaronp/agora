package jabroni.domain

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.exchange.WorkSubscription
import jabroni.api.json.JMatcher
import jabroni.api.worker.{HostLocation, WorkerDetails}

class WorkerConfig(config: Config = WorkerConfig.defaultConfig()) {
  val host = config.getString("host")
  val port = config.getInt("port")
  val location = HostLocation(host, port)
  val runUser: String = config.getString("runUser")

  def workerDetails: WorkerDetails = WorkerDetails(runUser, location)

  def matcher: JMatcher = ???
  def newWorkSubscription(i : Int) : WorkSubscription = {
    WorkSubscription(workerDetails, matcher)
  }
}

object WorkerConfig {
  def defaultConfig() = ConfigFactory.load().getConfig("jabroni.worker")

  def apply(): WorkerConfig = WorkerConfig()
}
