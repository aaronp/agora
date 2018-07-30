package riff.raft

/**
  * Represents the coords of a log entry
  * @param term
  * @param index
  */
final case class LogCoords(term : Int, index : Int) {
  require(term > 0)
  require(index > 0)
  def inc: LogCoords = copy(index + 1)
}

