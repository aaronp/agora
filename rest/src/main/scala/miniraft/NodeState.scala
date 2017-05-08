package miniraft

import jabroni.api.worker.HostLocation

case class NodeState(clusterByName : Map[String, HostLocation])
