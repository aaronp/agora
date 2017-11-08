package agora.rest.worker

import java.util.concurrent.atomic.AtomicInteger

import agora.api.`match`.MatchDetails
import agora.api.exchange.{Exchange, PendingSubscription, WorkSubscription}
import agora.api.worker.SubscriptionKey
import agora.rest.{BaseRoutesSpec, MatchDetailsExtractor}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

class RouteSubscriptionSupportTest extends BaseRoutesSpec {

  "RouteSubscriptionSupport.takeNextOnComplete" should {
    "not request work when a route doesn't match" in {
      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 0
      }

      Get("/nomatch") ~> routes(ReplaceOne) ~> check {
        response.status.intValue() shouldBe 404
        currentSubscription.requested shouldBe 0
      }
      Get("/nope") ~> routes(ReplaceOne) ~> check {
        response.status.intValue() shouldBe 404
        currentSubscription.requested shouldBe 0
      }
    }
    "not request work when a route matches but doesn't look as though it were sent from an exchange" in {
      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 0
      }

      val routesUnderTest = routes(ReplaceOne)

      Get("/testing") ~> routesUnderTest ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[Int] shouldBe 1
        currentSubscription.requested shouldBe 0
      }
      Get("/testing") ~> routesUnderTest ~> check {
        response.status shouldBe StatusCodes.OK
        responseAs[Int] shouldBe 2
        currentSubscription.requested shouldBe 0
      }
    }
    "request work for a fixed amount of work items after the response completes" in {
      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 0
      }

      // we should start out w/ pulling a request

      exchange.request(Scenario.key, 1).futureValue
      currentSubscription.requested shouldBe 1

      val routesUnderTest = routes(Scenario.SetPendingTarget(5))

      import agora.api.Implicits._

      (1 to 3).foreach { expected =>
        exchange.submit("doesn't matter".asJob).futureValue

        val remaining = currentSubscription.requested

        val details = MatchDetails.empty.copy(subscriptionKey = Scenario.key, remainingItems = remaining)
        val req     = Get("/testing").withHeaders(MatchDetailsExtractor.headersFor(details))

        req ~> routesUnderTest ~> check {
          withClue(s"When told there are $remaining work items remaining it should request 5 - $remaining items") {

            response.status shouldBe StatusCodes.OK

            // the test route echos back the number of times it was called,
            // which happens to match our test input remaining items
            responseAs[Int] shouldBe expected

            currentSubscription.requested shouldBe 5
          }
        }
      }
    }
    "request more work for requests with match details after the response completes" in {

      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 0
      }

      val details = MatchDetails.empty.copy(subscriptionKey = Scenario.key)
      val req     = Get("/testing").withHeaders(MatchDetailsExtractor.headersFor(details))

      val routeUnderTest = routes(ReplaceOne)

      (1 to 3).foreach { expected =>
        req ~> routeUnderTest ~> check {
          response.status shouldBe StatusCodes.OK
          responseAs[Int] shouldBe expected
          currentSubscription.requested shouldBe expected
        }
      }
    }
  }

  trait TestScenario extends RouteSubscriptionSupport {
    val exchange             = Exchange.instance()
    val key: SubscriptionKey = exchange.subscribe(WorkSubscription.localhost(1234)).futureValue.id

    /** @return the current subscription state from the exchange.
      */
    def currentSubscription: PendingSubscription = {
      exchange.queueState().futureValue.subscriptions.ensuring(_.size == 1).head
    }

    // create a new support instance which will take another element from the exchange on completion
    def mkRoutes(action: TakeAction) = {
      val calls = new AtomicInteger(0)

      /*
       * OUR DIRECTIVE UNDER TEST
       * vvvvvvvvvvvvvvvvvvvvvvvv
       */
      takeNextOnComplete(exchange, action) {
        /* ^^^^^^^^^^^^^^^^^^^^^^^^^
         * OUR DIRECTIVE UNDER TEST
         */

        (get & path("testing")) {
          complete(
            calls.incrementAndGet()
          )
        }
      }
    }

    def routes(action: TakeAction) = Route.seal(mkRoutes(action))
  }

}
