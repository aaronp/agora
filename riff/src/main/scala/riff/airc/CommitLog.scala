package riff.airc

import LogCoords

final case class CommitLog(lastCommitted : LogCoords, uncommitted : Seq[LogCoords])
