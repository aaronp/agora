package agora.exec.events

import agora.BaseExecApiSpec
import agora.api.`match`.MatchDetails
import agora.time.now
import agora.exec.model.RunProcess
import io.circe.Decoder.Result
import io.circe.syntax._

import scala.util.Try

class EventQueryResponseTest extends BaseExecApiSpec {

  "EventQueryResponse" should {

    List[EventQueryResponse](
      findResponse,
      NotFinishedBetweenResponse(Nil),
      NotFinishedBetweenResponse(List(startedJob)),
      NotStartedBetweenResponse(Nil),
      NotStartedBetweenResponse(List(received)),
      StartTimesBetweenResponse(Nil),
      StartTimesBetweenResponse(List(StartedSystem(json""" { "conf" : "ig" } """))),
      FindFirstResponse(None),
      FindFirstResponse(Option(now()))
    ).foreach { expected =>
      expected.toString should {
        "be serializable to/from json" in {
          expected.asJson.as[EventQueryResponse] shouldBe Right(expected)
        }
      }
    }
  }

  def received = ReceivedJob("someob", Option(MatchDetails("a", "b", "c", 1, 2)), RunProcess("go"))

  def startedJob = StartedJob("someob").copy(details = Option(received))

  def findResponse: FindJobResponse = {
    FindJobResponse(
      Option(received),
      Option(startedJob),
      Option(CompletedJob("someob", Try(123)).copy(details = Option(received))),
      Option(123)
    )
  }
}
