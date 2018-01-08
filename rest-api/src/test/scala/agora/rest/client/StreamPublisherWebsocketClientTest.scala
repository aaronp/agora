package agora.rest.client

import agora.BaseSpec
import agora.api.streams.BasePublisher
import agora.rest.exchange.TakeNext
import org.scalatest.{FunSuite, Matchers}
import io.circe.generic.auto._

class StreamPublisherWebsocketClientTest extends BaseSpec with Matchers {

  "StreamPublisherWebsocketClient" should {
    "work" in {

      val somePublisher = BasePublisher[String](10)


      // create the publisher under test which will create a flow for our underlying 'somePublisher'
      val spwc: StreamPublisherWebsocketClient[String, BasePublisher[String]] = {
        new StreamPublisherWebsocketClient[String, BasePublisher[String]](somePublisher)
      }


      // we should be able to pull from the 'pullsFromPFP', but only when a client message comes through
      // to the controlMessageProcessor should items be pulled

      spwc.controlMessageProcessor.onNext(TakeNext(1))

      ???
    }
  }
}
