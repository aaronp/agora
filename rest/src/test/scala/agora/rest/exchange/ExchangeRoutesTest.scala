package agora.rest.exchange

import akka.http.scaladsl.server.Route
import agora.api.Implicits._
import agora.api.nextJobId
import agora.api.exchange.{QueueStateResponse, UpdateSubscriptionAck, _}
import agora.api.worker.{HostLocation, SubscriptionKey, WorkerDetails}
import agora.rest.BaseRoutesSpec
import io.circe.Json
import io.circe.generic.auto._
import io.circe.optics.JsonPath

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.reflectiveCalls

/**
  * In this test, we could assert the response marshalling,
  * but it's worth as well having tests which cover explicit json as strings, just in case we accidentally break
  * that form by e.g. renaming a parameter. that would potentially break clients running against different
  * versions of our service, or dynamic languages (e.g. javascript )
  */
class ExchangeRoutesTest extends BaseRoutesSpec {

  def routes(obs: MatchObserver = MatchObserver()): Route = {
    exchangeRoutes(obs).routes
  }

  def exchangeRoutes(obs: MatchObserver) = {
    val exchange = Exchange(obs)
    ExchangeRoutes(new ServerSideExchange(exchange, obs))
  }

  "PUT /rest/exchange/submit" should {
    "submit jobs" in {
      val obs = MatchObserver()
      ExchangeHttp(123.asJob().withAwaitMatch(false)) ~> routes(obs) ~> check {
        val resp = responseAs[SubmitJobResponse]
        resp.id should not be (null)
      }
    }
  }
  "POST /rest/exchange/update/<id>" should {
    "return an empty ack of the subscription does not exist" in {
      val obs            = MatchObserver()
      val routeUnderTest = exchangeRoutes(obs)
      val original       = WorkSubscription.localhost(1234).withSubscriptionKey("testing").append("original", "alpha").append("unchanged", "constant")
      val ack            = routeUnderTest.exchange.subscribe(original).futureValue
      ack.id shouldBe "testing"

      val newDetails = WorkerDetails(Json.Null).append("original", "beta").append("appended", "true")

      def verifyUpdatedJson(json: Json) = {
        JsonPath.root.original.string.getOption(json) shouldBe Some("beta")
        JsonPath.root.appended.string.getOption(json) shouldBe Some("true")
        JsonPath.root.unchanged.string.getOption(json) shouldBe Some("constant")
      }

      ExchangeHttp("testing", newDetails) ~> routeUnderTest.routes ~> check {
        val UpdateSubscriptionAck("testing", Some(oldDetails), Some(updated)) = responseAs[UpdateSubscriptionAck]
        oldDetails shouldBe original.details

        val expected = newDetails.append("unchanged", "constant")

        verifyUpdatedJson(updated.aboutMe)
      }

      val QueueStateResponse(Nil, List(PendingSubscription("testing", subscription, 0))) =
        routeUnderTest.exchange.queueState(QueueState(workerSubscriptionMatcher = "id" === "testing")).futureValue

      verifyUpdatedJson(subscription.details.aboutMe)
    }
    "update subscriptions" in {
      val obs = MatchObserver()
      ExchangeHttp(123.asJob().withAwaitMatch(false)) ~> routes(obs) ~> check {
        val resp = responseAs[SubmitJobResponse]
        resp.id should not be (null)
      }
    }
  }
  "PUT /rest/exchange/subscribe" should {
    "subscribe for work" in {
      ExchangeHttp(WorkSubscription.localhost(1234)) ~> routes() ~> check {
        val resp = responseAs[WorkSubscriptionAck]
        resp.id should not be (null)
      }
    }
  }
  "POST /rest/exchange/take" should {
    "take work for a subscription" in {

      val obs = MatchObserver()

      val route = exchangeRoutes(obs)

      var subscription: SubscriptionKey = null

      val job        = 123.asJob(SubmissionDetails(awaitMatch = true)).withId(nextJobId())
      val expectedId = job.jobId.get

      val matchFuture: Future[BlockingSubmitJobResponse] = obs.onJob(job)

      // subscribe to work
      val ws = WorkSubscription(HostLocation.localhost(1234)).withSubscriptionKey("i'll tell you the key, thank you very much!")
      //ws.details.id should not be(None)
      ExchangeHttp(ws) ~> route.routes ~> check {
        val resp = responseAs[WorkSubscriptionAck]
        subscription = resp.id
      }
      matchFuture.isCompleted shouldBe false

      // now pull the job
      ExchangeHttp(RequestWork(subscription, 2)) ~> route.routes ~> check {
        val RequestWorkAck(subsctriptionId, _, total) = responseAs[RequestWorkAck]
        subsctriptionId shouldBe subscription
        total shouldBe 2
      }

      // now push the job
      ExchangeHttp(job) ~> route.routes ~> check {
        val resp = responseAs[BlockingSubmitJobResponse]
        resp.jobId shouldBe expectedId
        resp.workers shouldBe List(ws.details)
      }

      val matchRes: BlockingSubmitJobResponse = matchFuture.futureValue
      matchRes.jobId shouldBe job.jobId.get
      matchRes.workers shouldBe List(ws.details)
    }
  }
}
