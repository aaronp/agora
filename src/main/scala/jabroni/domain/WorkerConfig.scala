package jabroni.domain

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.json.JMatcher
import jabroni.api.worker.{HostLocation, RequestWork, WorkerDetails}

import scala.util.Properties

class WorkerConfig(config: Config = WorkerConfig.defaultConfig()) {
  val host = config.getString("host")
  val port = config.getInt("port")
  val location = HostLocation(host, port)
  val runUser: String = config.getString("runUser")

  def workerDetails: WorkerDetails = WorkerDetails(runUser, location)

  def matcher: JMatcher = ???
  def newRequestWork(i : Int) : RequestWork = {
    RequestWork(workerDetails, matcher, i)
  }
}

object WorkerConfig {
  def defaultConfig() = ConfigFactory.load().getConfig("jabroni.worker")

  def apply(): WorkerConfig = WorkerConfig()
}
