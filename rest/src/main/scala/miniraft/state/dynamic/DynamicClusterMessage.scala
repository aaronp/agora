package miniraft.state

import agora.api.config.HostLocation

sealed trait DynamicClusterMessage

case class AddHost(server: HostLocation) extends DynamicClusterMessage

case class RemoveHost(server: HostLocation) extends DynamicClusterMessage
