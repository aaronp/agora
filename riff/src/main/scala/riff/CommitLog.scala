package riff

import monix.execution.Scheduler
import monix.reactive.{Observable, Pipe}
import riff.raft.LogCoords

import scala.util.control.NonFatal

trait CommitLog[T] {
  def append(coords: LogCoords, data: T)

  def commit(coords: LogCoords)
}

object CommitLog {

  sealed trait LogEvent

  final case class DataAppended[T](coords: LogCoords, data: T) extends LogEvent

  final case class DataCommitted(coords: LogCoords) extends LogEvent


  def apply[T](underlying: CommitLog[T])(implicit sched: Scheduler): CommitLog[T] = {
    new Wrap(underlying)
  }

  class Wrap[T](underlying: CommitLog[T])(implicit sched: Scheduler) extends CommitLog[T] {

    private val (logObserver, logObservable) = Pipe.publish[LogEvent].multicast

    def log: Observable[LogEvent] = logObservable

    //def log: Publisher[LogEvent]
    override def append(coords: LogCoords, data: T): Unit = {
      try {
        underlying.append(coords, data)
        logObserver.onNext(DataAppended(coords, data))
      } catch {
        case NonFatal(e) =>
          logObserver.onError(LogAppendException(coords, data, e))
      }
    }

    override def commit(coords: LogCoords): Unit = {
      ???
    }
  }

}