package riff

import riff.raft.LogCoords

final case class AppendResult(logCoods : LogCoords, appended : Map[String, Boolean], clusterSize : Int)
