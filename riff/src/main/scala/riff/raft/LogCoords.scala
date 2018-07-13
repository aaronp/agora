package riff.raft

/**
  * Represents the coords of a log entry
  * @param term
  * @param index
  */
final case class LogCoords(term : Int, index : Int)
