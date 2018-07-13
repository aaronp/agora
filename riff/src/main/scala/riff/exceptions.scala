package riff

import riff.raft.{LeaderOpinion, LogCoords}

final case class RedirectException(leaderOpinion: LeaderOpinion) extends Exception(s"${leaderOpinion}")

final case class LogAppendException[T](coords: LogCoords, data: T, err: Throwable) extends Exception(s"Error appending ${coords} : $data", err)
