package riff

import monix.execution.Scheduler
import monix.reactive.{Observable, Pipe}
import riff.raft.LogCoords

import scala.util.control.NonFatal

/**
  * Represents the commit log
  *
  * @tparam T
  */
trait CommitLog[T] {
  def append(coords: LogCoords, data: T)

  def commit(coords: LogCoords)
}

object CommitLog {

  sealed trait LogEvent

  final case class DataAppended[T](coords: LogCoords, data: T) extends LogEvent

  final case class DataCommitted(coords: LogCoords) extends LogEvent

  def inMemory[T](implicit sched: Scheduler) = new ObservableLog(InMemory())

  def apply[L <: CommitLog[T], T](underlying: L)(implicit sched: Scheduler) = new ObservableLog(underlying)

  final case class InMemory[T]() extends CommitLog[T] {
    private val uncommittedByCoords = new java.util.concurrent.ConcurrentHashMap[LogCoords, T]()
    private val committedByCoords = new java.util.concurrent.ConcurrentHashMap[LogCoords, T]()

    override def append(coords: LogCoords, data: T): Unit = {
      uncommittedByCoords.put(coords, data)
    }

    override def commit(coords: LogCoords): Unit = {
      val data = uncommittedByCoords.remove(coords)
      require(data != null)
      committedByCoords.put(coords, data)
    }

    def getUncommitted(c: LogCoords): Option[T] = Option(uncommittedByCoords.get(c))

    def getCommitted(c: LogCoords): Option[T] = Option(committedByCoords.get(c))

    def uncommitted: Set[LogCoords] = {
      import scala.collection.JavaConverters._
      uncommittedByCoords.keySet().asScala.toSet
    }

    def committed: Set[LogCoords] = {
      import scala.collection.JavaConverters._
      committedByCoords.keySet().asScala.toSet
    }
  }

  case class ObservableLog[T, L <: CommitLog[T]](underlying: L)(implicit sched: Scheduler) extends CommitLog[T] {

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
      try {
        underlying.commit(coords)
        logObserver.onNext(DataCommitted(coords))
      } catch {
        case NonFatal(e) =>
          logObserver.onError(LogCommitException(coords, e))
      }
    }
  }

}