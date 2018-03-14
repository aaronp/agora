package agora.exec.events

import agora.api.JobId
import agora.json.AgoraJsonImplicits
import agora.time.Timestamp
import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

/**
  * Represents a query of the system state
  */
sealed trait EventQuery {
  type Response <: EventQueryResponse
}

object EventQuery {
  type Aux[T] = EventQuery { type Response = T }

  implicit def queryAsAux[T](query: EventQuery { type Response = T }): Aux[T] = query

  implicit def auxAsQuery[T](query: EventQuery.Aux[T]): EventQuery = query

  implicit object EventQueryFormat extends Encoder[EventQuery] with Decoder[EventQuery] with AgoraJsonImplicits {

    import io.circe.generic.auto._

    override def apply(eventQuery: EventQuery): Json = {
      eventQuery match {
        case FindJob(id)               => Json.obj("findJob" -> id.asJson)
        case FindFirst(event)          => Json.obj("findFirst" -> event.asJson)
        case value: ReceivedBetween    => value.asJsonObject.add("type", "ReceivedBetween".asJson).asJson
        case value: StartedBetween     => value.asJsonObject.add("type", "StartedBetween".asJson).asJson
        case value: CompletedBetween   => value.asJsonObject.add("type", "CompletedBetween".asJson).asJson
        case value: NotFinishedBetween => value.asJsonObject.add("type", "NotFinishedBetween".asJson).asJson
        case value: NotStartedBetween  => value.asJsonObject.add("type", "NotStartedBetween".asJson).asJson
        case value: StartTimesBetween  => value.asJsonObject.add("type", "StartTimesBetween".asJson).asJson
      }
    }

    override def apply(c: HCursor): Result[EventQuery] = {
      import cats.syntax.either._

      def asFindJob: Result[FindJob] = {
        for {
          id <- c.downField("findJob").as[String]
        } yield {
          FindJob(id)
        }
      }

      def asFindFirst: Result[FindFirst] = {
        c.downField("findFirst").as[String].flatMap { event =>
          def err: Result[FindFirst] = Left(DecodingFailure(s"Invalid event '$event'", c.history))

          FindFirst.valueOf(event).fold(err)(Right.apply)
        }
      }

      def asCriteria: Either[DecodingFailure, EventQuery] = {
        c.downField("type").as[String].flatMap {
          case "ReceivedBetween"    => c.as[ReceivedBetween]
          case "StartedBetween"     => c.as[StartedBetween]
          case "CompletedBetween"   => c.as[CompletedBetween]
          case "NotFinishedBetween" => c.as[NotFinishedBetween]
          case "NotStartedBetween"  => c.as[NotStartedBetween]
          case "StartTimesBetween"  => c.as[StartTimesBetween]
          case other                => Left(DecodingFailure(s"Invalid query type '$other'", c.history))
        }
      }

      asFindJob.orElse(asCriteria).orElse(asFindFirst)
    }
  }

}

sealed trait EventQueryResponse

object EventQueryResponse {

  implicit object EventQueryResponseFormat extends Encoder[EventQueryResponse] with Decoder[EventQueryResponse] with AgoraJsonImplicits {

    import io.circe.generic.auto._

    override def apply(eventQuery: EventQueryResponse): Json = {
      eventQuery match {
        case value: FindJobResponse            => value.asJsonObject.add("type", "FindJobResponse".asJson).asJson
        case value: FindFirstResponse          => value.asJsonObject.add("type", "FindFirstResponse".asJson).asJson
        case value: ReceivedBetweenResponse    => value.asJson
        case value: StartedBetweenResponse     => value.asJson
        case value: CompletedBetweenResponse   => value.asJson
        case value: NotFinishedBetweenResponse => value.asJson
        case value: NotStartedBetweenResponse  => value.asJson
        case value: StartTimesBetweenResponse  => value.asJson
      }
    }

    override def apply(c: HCursor): Result[EventQueryResponse] = {
      import cats.syntax.either._

      def asFindResponse = {
        c.downField("type").as[String].flatMap {
          case "FindJobResponse"   => c.as[FindJobResponse]
          case "FindFirstResponse" => c.as[FindFirstResponse]
          case other               => Left(DecodingFailure(s"Invalid query response  type '$other'", c.history))
        }
      }

      c.as[ReceivedBetweenResponse]
        .orElse(c.as[StartedBetweenResponse])
        .orElse(c.as[CompletedBetweenResponse])
        .orElse(c.as[NotFinishedBetweenResponse])
        .orElse(c.as[NotStartedBetweenResponse])
        .orElse(c.as[StartTimesBetweenResponse])
        .orElse(asFindResponse)
    }
  }

}

/**
  * Get a job by its ID
  */
case class FindJob(id: JobId) extends EventQuery {
  override type Response = FindJobResponse
}

case class FindJobResponse(received: Option[ReceivedJob], started: Option[StartedJob], completed: Option[CompletedJob], tookInMillis: Option[Long])
    extends EventQueryResponse

/**
  * Received jobs within the time range -- they may not have been started, however
  */
case class ReceivedBetween(from: Timestamp, to: Timestamp, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = ReceivedBetweenResponse
}

case class ReceivedBetweenResponse(received: List[ReceivedJob]) extends EventQueryResponse

/**
  * Jobs which had fulfilled all their dependencies and thus were started within the time range
  */
case class StartedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = StartedBetweenResponse
}

case class StartedBetweenResponse(started: List[StartedJob]) extends EventQueryResponse

/**
  * Completed jobs within the time range
  */
case class CompletedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = CompletedBetweenResponse
}

case class CompletedBetweenResponse(completed: List[CompletedJob]) extends EventQueryResponse

/**
  * Jobs which are were started but not completed within the time range
  */
case class NotFinishedBetween(from: Timestamp, to: Timestamp, verbose: Boolean = false, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = NotFinishedBetweenResponse
}

case class NotFinishedBetweenResponse(running: List[StartedJob]) extends EventQueryResponse

/**
  * Jobs which are were received but not started within the time range
  */
case class NotStartedBetween(from: Timestamp, to: Timestamp, filter: JobFilter = JobFilter.empty) extends EventQuery {
  override type Response = NotStartedBetweenResponse
}

case class NotStartedBetweenResponse(pending: List[ReceivedJob]) extends EventQueryResponse

/**
  * System startups between the given times
  */
case class StartTimesBetween(from: Timestamp, to: Timestamp) extends EventQuery {
  override type Response = StartTimesBetweenResponse
}

case class StartTimesBetweenResponse(systemStarts: List[StartedSystem]) extends EventQueryResponse

/**
  * Means to establish the first known timetamp for a particular event
  */
case class FindFirst private (eventName: String) extends EventQuery {
  override type Response = FindFirstResponse
}

object FindFirst {
  def valueOf(event: String) = values.find(_.eventName == event)

  val started   = FindFirst("started")
  val received  = FindFirst("received")
  val completed = FindFirst("completed")
  val values    = Set(received, started, completed)
  require(!values.contains(null))

  def validValues = values.map(_.eventName)
}

case class FindFirstResponse(firstTimestamp: Option[Timestamp]) extends EventQueryResponse
