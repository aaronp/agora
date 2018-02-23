package agora.exec.events

import agora.BaseSpec
import agora.api.time._
import io.circe.syntax._

class EventQueryTest extends BaseSpec {

  "EventQuery" should {

    val from = now()
    val to   = from.plusHours(1)
    List[EventQuery](
      FindJob("xyz"),
      ReceivedBetween(from, to, JobFilter("foo")),
      StartedBetween(from, to, false, JobFilter("foo")),
      StartedBetween(from, to, verbose = true),
      CompletedBetween(from, to, true, JobFilter("foo")),
      NotFinishedBetween(from, to, true, JobFilter("foo")),
      NotStartedBetween(from, to, JobFilter("foo")),
      StartTimesBetween(from, to),
      FindFirst.started,
      FindFirst.received,
      FindFirst.completed
    ).foreach { expected =>
      expected.toString should {
        "be serializable to/from json" in {
          expected.asJson.as[EventQuery] shouldBe Right(expected)
        }
      }
    }
  }
}
