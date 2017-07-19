package agora.rest.worker

import agora.api.exchange.{Exchange, MatchObserver, PendingSubscription, WorkSubscription}
import agora.api.worker.SubscriptionKey
import agora.rest.BaseRoutesSpec
import akka.http.scaladsl.server.Directives._

class RouteSubscriptionSupportTest extends BaseRoutesSpec {

  "RouteSubscriptionSupport.takeNextOnComplete" should {
    "not request work when a route doesn't match" in {
      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 1
      }

      Get("nomatch") ~> routes ~> check {
        response.status.intValue() shouldBe 404
        currentSubscription.requested shouldBe 1
      }
      Get("nope") ~> routes ~> check {
        response.status.intValue() shouldBe 404
        currentSubscription.requested shouldBe 1
      }
    }
    "request more work when a response completes" in {

      object Scenario extends TestScenario
      import Scenario._

      currentSubscription.key shouldBe Scenario.key

      withClue("the initial subscription should be one") {
        currentSubscription.requested shouldBe 1
      }

      Get("testing") ~> routes ~> check {
        responseAs[Int] shouldBe 1
        currentSubscription.requested shouldBe 2
      }

      // and again
      Get("testing") ~> routes ~> check {
        responseAs[Int] shouldBe 2
        currentSubscription.requested shouldBe 3
      }
    }
  }

  trait TestScenario {
    val exchange             = Exchange.instance()
    val key: SubscriptionKey = exchange.subscribe(WorkSubscription()).futureValue.id

    /** @return the current subscription state from the exchange.
      */
    def currentSubscription: PendingSubscription = {
      exchange.queueState().futureValue.subscriptions.ensuring(_.size == 1).head
    }

    // create a new support instance which will take another element from the exchange on completion
    val underTest = new RouteSubscriptionSupport {
      def routes = {
        var calls = 0
        get {
          takeNextOnComplete(exchange, key) {
            path("testing") {
              calls = calls + 1
              complete(
                calls
              )
            }
          }
        }
      }
    }

    val routes = underTest.routes
  }

}
