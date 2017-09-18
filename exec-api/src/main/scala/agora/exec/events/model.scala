package agora.exec.events

import java.nio.file
import java.time.LocalDateTime

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.api.time.now
import agora.exec.model.RunProcess
import agora.io.dao.{HasId, Persist}

import scala.concurrent.Future
import scala.reflect.io.Path
import scala.util.Try

/**
  * Represents a system event we want to write down
  */
sealed trait RecordedEvent

sealed trait EventQuery {
  type Response
}

/**
  * The event monitor is where we sent event notifications we care about (jobs started, stopped, failed, etc)
  */
trait SystemEventMonitor {
  def accept(event: RecordedEvent): Unit

  def query(query: EventQuery): Future[query.Response]
}

object SystemEventMonitor {

  object DevNull extends SystemEventMonitor {
    override def accept(event: RecordedEvent): Unit = {}

    override def query(query: EventQuery): Future[query.Response] = Future.failed(new Exception(s"dev/null ignoring $query"))
  }

}

case class ReceivedJob(id: JobId, details: Option[MatchDetails], job: RunProcess, received: LocalDateTime = now()) extends RecordedEvent

case class CompletedJob(id: JobId, exitCode: Try[Int], completed: LocalDateTime = now()) extends RecordedEvent

object ReceivedJob {

  implicit object ReceivedJobId extends HasId[ReceivedJob] {
    override def id(value: ReceivedJob): String = value.id
  }

}
