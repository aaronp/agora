package agora.health

import agora.api.BaseSpec
import agora.api.exchange._
import agora.rest.HasMaterializer
import io.circe.optics.JsonPath

class HealthUpdateTest extends BaseSpec with HasMaterializer {

  "HealthUpdate" should {
    "periodically update the health data" in {
      val exchange                             = Exchange.instance()
      val WorkSubscriptionAck(subscriptionKey) = exchange.subscribe(WorkSubscription.localhost(5678)).futureValue

      import materializer.executionContext
      val path = JsonPath.root.health.objectPendingFinalizationCount.int

      def update(id: Int) = {
        val future                                                          = HealthUpdate.updateHealth(exchange, subscriptionKey, HealthDto().copy(objectPendingFinalizationCount = id))
        val UpdateSubscriptionAck(`subscriptionKey`, Some(b4), Some(after)) = future.futureValue
        val id1                                                             = path.getOption(b4.aboutMe).getOrElse(0)
        val id2                                                             = path.getOption(after.aboutMe).getOrElse(0)
        (id1, id2)
      }

      // prove that the health updates are updating our subscription
      update(123) shouldBe (0, 123)
      update(456) shouldBe (123, 456)
      update(789) shouldBe (456, 789)

      // verify the exchange by querying it directly
      val QueueStateResponse(Nil, List(PendingSubscription(`subscriptionKey`, subscription, 0))) = exchange.queueState().futureValue
      path.getOption(subscription.details.aboutMe).getOrElse(0) shouldBe 789
    }
  }
}
