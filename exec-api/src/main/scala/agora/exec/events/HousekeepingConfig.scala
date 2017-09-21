package agora.exec.events

import agora.config.RichConfig.implicits._
import com.typesafe.config.Config

case class HousekeepingConfig(config: Config) {

  def removeEventsOlderThan = config.asFiniteDuration("removeEventsOlderThan")

  def removeWorkspacesOlderThan = config.asFiniteDuration("removeWorkspacesOlderThan")

  def checkEvery = config.asFiniteDuration("checkEvery")

}
