package agora.exec.events

import agora.api.JobId
import agora.api.time.Timestamp

/**
  * Represents a query of the system state
  */
sealed trait EventQuery {
  type Response
}

/**
  * Represents a filter. We may change this to a sealed trait in future
  * @param contains
  */
case class JobFilter(contains: String = "") {
  def accept(details: Option[ReceivedJob]): Boolean = details.exists(matches)
  def matches(details: ReceivedJob): Boolean = {
    details.job.commandString.contains(contains)
  }

  def isEmpty: Boolean  = contains.isEmpty
  def nonEmpty: Boolean = contains.nonEmpty

}

object JobFilter {
  val empty = JobFilter("")
}

object EventQuery {
  type Aux[T] = EventQuery { type Response = T }

  implicit def queryAsAux[T](query: EventQuery { type Response = T }): Aux[T] = query

  implicit def auxAsQuery[T](query: EventQuery.Aux[T]): EventQuery = query
}

/**
  * Received jobs within the time range -- they may not have been started, however
  */
case class ReceivedBetween(from: Timestamp, to: Timestamp, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = ReceivedBetweenResponse
}

case class ReceivedBetweenResponse(received: List[ReceivedJob])

/**
  * Jobs which had fulfilled all their dependencies and thus were started within the time range
  */
case class StartedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = StartedBetweenResponse
}

case class StartedBetweenResponse(started: List[StartedJob])

/**
  * Completed jobs within the time range
  */
case class CompletedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = CompletedBetweenResponse
}

case class CompletedBetweenResponse(completed: List[CompletedJob])

/**
  * Get a job by its ID
  */
case class FindJob(id: JobId) extends EventQuery {
  override type Response = FindJobResponse
}

case class FindJobResponse(job: Option[ReceivedJob], started: Option[StartedJob], completed: Option[CompletedJob], tookInMillis: Option[Long])

/**
  * Jobs which are were started but not completed within the time range
  */
case class NotFinishedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = NotFinishedBetweenResponse
}

case class NotFinishedBetweenResponse(jobs: List[StartedJob])

/**
  * Jobs which are were received but not started within the time range
  */
case class NotStartedBetween(from: Timestamp, to: Timestamp, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = NotStartedBetweenResponse
}

case class NotStartedBetweenResponse(jobs: List[ReceivedJob])

/**
  * Jobs which are were received but not started within the time range
  */
case class StartTimesBetween(from: Timestamp, to: Timestamp) extends EventQuery {
  override type Response = StartTimesBetweenResponse
}

case class StartTimesBetweenResponse(starts: List[StartedSystem])

/**
  * Jobs which are were received but not started within the time range
  */
case class FindFirst private (eventName: String) extends EventQuery {
  override type Response = FindFirstResponse
}

object FindFirst {
  val started     = FindFirst("started")
  val received    = FindFirst("received")
  val completed   = FindFirst("completed")
  val values      = Set(received, started, completed)
  def validValues = values.map(_.eventName)
}

case class FindFirstResponse(timestamp: Option[Timestamp])
