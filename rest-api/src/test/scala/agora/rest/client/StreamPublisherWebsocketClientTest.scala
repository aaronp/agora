package agora.rest.client

import agora.BaseSpec
import agora.api.streams.{BasePublisher, ListSubscriber}
import org.scalatest.Matchers

class StreamPublisherWebsocketClientTest extends BaseSpec with Matchers {

  "StreamPublisherWebsocketClient.pullsFromPFP" should {
    "only pull messages when a TakeNext ClientSubscriptionMessage is received" in {

      val somePublisher = BasePublisher[String](10)

      // create the publisher under test which will create a flow for our underlying 'somePublisher'
      val spwc: StreamPublisherWebsocketClient[String, BasePublisher[String]] = {
        new StreamPublisherWebsocketClient[String, BasePublisher[String]](somePublisher)
      }

      // we should be able to pull from the 'pullsFromPFP', but only when a client message comes through
      // to the controlMessageProcessor should items be pulled

      val testSubscriber = new ListSubscriber[String]()
      spwc.pullsFromPFP.subscribe(testSubscriber)
      testSubscriber.request(3)

      withClue("no events should be pulled from the source publisher until a TakeNext ClientSubscriptionMessage is received") {
        testSubscriber.received() shouldBe Nil
        somePublisher.publish("first")
        somePublisher.publish("second")
        somePublisher.publish("third")
        somePublisher.publish("fourth")
        testSubscriber.received() shouldBe Nil
      }

      // call the method under test - explicitly requesting via StreamPublisherWebsocketClient.request
      spwc.takeNext(2)

      withClue("two events should now be published") {
        testSubscriber.received() shouldBe List("second", "first")
        spwc.takeNext(2)
        testSubscriber.received() shouldBe List("third", "second", "first")
      }

    }
  }
}
