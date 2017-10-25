package agora.exec.events

import java.util.UUID

import agora.api.JobId
import agora.api.`match`.MatchDetails
import agora.api.time.{now, _}
import agora.exec.model.RunProcess
import agora.io.dao.HasId
import com.typesafe.config.Config
import io.circe.Json

import scala.util.Try

/**
  * Represents a system event we want to write down
  */
sealed trait RecordedEvent

case class ReceivedJob(id: JobId, details: Option[MatchDetails], job: RunProcess, received: Timestamp = now()) extends RecordedEvent

object ReceivedJob {

  implicit object ReceivedJobId extends HasId[ReceivedJob] {
    override def id(value: ReceivedJob): String = value.id
  }

}

/**
  * An Event which signals the intent to remove records before a given timestamp
  * @param before
  */
case class DeleteBefore(before: Timestamp) extends RecordedEvent

case class StartedJob(id: JobId, started: Timestamp = now(), details: Option[ReceivedJob] = None) extends RecordedEvent

object StartedJob {

  implicit object StartedJobId extends HasId[StartedJob] {
    override def id(value: StartedJob): String = value.id
  }

}

case class CompletedJob(id: JobId, exitCode: Try[Int], completed: Timestamp = now(), details: Option[ReceivedJob] = None) extends RecordedEvent

object CompletedJob {

  implicit object CompletedJobId extends HasId[CompletedJob] {
    override def id(value: CompletedJob): String = value.id
  }

}

case class StartedSystem(config: Json, jvmId: String = StartedSystem.jvmId, startTime: Timestamp = now()) extends RecordedEvent {
  def id = s"${jvmId}_${startTime}"
}

object StartedSystem {
  private val jvmId = UUID.randomUUID().toString

  implicit object StartedSystemId extends HasId[StartedSystem] {
    override def id(value: StartedSystem): String = value.id
  }

}
