package riff

final case class CommitLog(latestEntryTerm : Int, latestEntryCommitIndex : Int)
