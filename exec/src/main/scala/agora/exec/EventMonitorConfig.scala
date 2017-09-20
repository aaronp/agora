package agora.exec

import agora.exec.events.SystemEventMonitor
import agora.rest.AkkaImplicits
import com.typesafe.config.Config

case class EventMonitorConfig(config: Config)(implicit akkaImplicits: AkkaImplicits) {

  def eventMonitor: SystemEventMonitor = defaultEventMonitor

  def enabled = config.getBoolean("on")

  lazy val defaultEventMonitor: SystemEventMonitor = {
    if (enabled) {
      import agora.io.implicits._
      SystemEventMonitor(config.getString("dir").asPath)(akkaImplicits.system)
    } else {
      SystemEventMonitor.DevNull
    }
  }
}
