package agora.api.exchange.observer

import agora.BaseSpec
import agora.api.exchange.{Candidate, PendingSubscription, QueueStateResponse, WorkSubscription}
import agora.api.json.JsonDelta
import io.circe.parser.decode
import io.circe.syntax._

class ExchangeNotificationMessageTest extends BaseSpec {

  import agora.api.Implicits._
  import agora.api.time._

  val candidate = Candidate("foo", WorkSubscription.localhost(8888), 0)
  val list = List[ExchangeNotificationMessage](
    OnSubscriptionRequestCountChanged(now(), "some key", 1, 2),
    OnSubscriptionUpdated(now(), JsonDelta(List(("foo" === "bar").asPath), 567.asJson), candidate),
    OnJobSubmitted(now(), "foo".asJob),
    OnSubscriptionCreated(now(), candidate),
    OnJobsCancelled(now(), Set("the job id", "another")),
    OnSubscriptionsCancelled(now(), Set("subscription key")),
    OnMatch(now(), "job id here", "a job".asJob, List(candidate)),
    OnStateOfTheWorld(now(), QueueStateResponse(List("a job".asJob), List(PendingSubscription("key", WorkSubscription.localhost(1234), 1))))
  )

  list.foreach { expected =>
    s"${expected.getClass.getSimpleName}" should {
      "serialize to/from json" in {
        val jsonString = expected.asJson.spaces4
        decode[ExchangeNotificationMessage](jsonString) shouldBe Right(expected)
      }
    }
  }

}
