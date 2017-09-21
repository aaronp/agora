package agora.exec.events

import com.typesafe.config.Config

import agora.config.RichConfig.implicits._

case class HousekeepingConfig(config : Config) {

  def removeEventsOlderThan = config.asFiniteDuration("removeEventsOlderThan")

  def checkEvery = config.asFiniteDuration("checkEvery")

}
