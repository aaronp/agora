package miniraft.state

import agora.api.worker.HostLocation

sealed trait DynamicClusterMessage

case class AddHost(server: HostLocation) extends DynamicClusterMessage

case class RemoveHost(server: HostLocation) extends DynamicClusterMessage
