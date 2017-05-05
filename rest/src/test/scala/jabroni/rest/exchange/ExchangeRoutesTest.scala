package jabroni.rest.exchange

import akka.http.scaladsl.server.Route
import jabroni.api.Implicits._
import jabroni.api._
import jabroni.api.exchange._
import jabroni.api.worker.SubscriptionKey
import jabroni.rest.BaseSpec

import scala.concurrent.Future
import scala.language.reflectiveCalls

/**
  * In this test, we could assert the response marshalling,
  * but it's worth as well having tests which cover explicit json as strings, just in case we accidentally break
  * that form by e.g. renaming a parameter. that would potentially break clients running against different
  * versions of our service, or dynamic languages (e.g. javascript )
  */
class ExchangeRoutesTest extends BaseSpec {

  def routes(): Route = {
    ExchangeRoutes().routes
  }

  "PUT /rest/exchange/submit" should {
    "submit jobs" in {
      ExchangeHttp(123.asJob()) ~> routes() ~> check {
        val resp = responseAs[SubmitJobResponse]
        resp.id should not be (null)
      }
    }
  }
  "PUT /rest/exchange/subscribe" should {
    "subscribe for work" in {
      ExchangeHttp(WorkSubscription()) ~> routes() ~> check {
        val resp = responseAs[WorkSubscriptionAck]
        resp.id should not be (null)
      }
    }
  }
  "POST /rest/exchange/take" should {
    "take work for a subscription" in {
      val route = ExchangeRoutes()

      var subscription: SubscriptionKey = null

      val job = 123.asJob(SubmissionDetails(awaitMatch = true)).withId(nextJobId())
      val expectedId = job.jobId.get

      val matchFuture: Future[BlockingSubmitJobResponse] = route.observer.onJob(job)

      // subscribe to work
      val ws = WorkSubscription()
      ws.details.id shouldBe None
      ExchangeHttp(ws) ~> route.routes ~> check {
        val resp = responseAs[WorkSubscriptionAck]
        subscription = resp.id
      }
      matchFuture.isCompleted shouldBe false

      // now pull the job
      ExchangeHttp(RequestWork(subscription, 2)) ~> route.routes ~> check {
        val resp = responseAs[RequestWorkAck]
        resp.id shouldBe subscription
        resp.totalItemsPending shouldBe 2
      }

      // now push the job
      ExchangeHttp(job) ~> route.routes ~> check {
        val resp = responseAs[BlockingSubmitJobResponse]
        resp.id shouldBe expectedId
        resp.workers shouldBe List(ws.details)
      }

      val matchRes: BlockingSubmitJobResponse = matchFuture.futureValue
      matchRes.id shouldBe job.jobId.get
      matchRes.workers shouldBe List(ws.details)
    }
  }
}
