package agora.rest.exchange

import agora.BaseRestApiSpec
import agora.api.exchange.observer.ExchangeObserver
import agora.rest.HasMaterializer
import akka.http.scaladsl.model.ws.TextMessage
import io.circe.parser._
import org.scalatest.concurrent.Eventually

class ExchangeClientObserverTest extends BaseRestApiSpec with HasMaterializer with Eventually {

  "ExchangeClientObserver.ClientFlow.clientPublisher" should {
    "sent messages from the client publisher through the clientControlMessageSource" in {
      val localObserver: ExchangeObserver               = ExchangeObserver()
      val clientFlow: ExchangeClientObserver.ClientFlow = new ExchangeClientObserver.ClientFlow(localObserver)

      // track the messages which appear from the clientPublisher here:
      var msgList = List[ClientSubscriptionMessage]()
      clientFlow.clientControlMessageSource.runForeach {
        case TextMessage.Strict(json) =>
          msgList = msgList :+ decode[ClientSubscriptionMessage](json).right.get
        case other => sys.error(s"Got $other")
      }

      // messages pushed from the client publisher should appear in the 'clientControlMessageSource'
      clientFlow.clientPublisher.notifyOfRequest(1)
      clientFlow.clientPublisher.notifyOfRequest(2)
      clientFlow.clientPublisher.notifyOfCancel()

      eventually {
        msgList should contain inOrderOnly (ClientSubscriptionMessage.takeNext(1), ClientSubscriptionMessage.takeNext(2), ClientSubscriptionMessage.cancel)
      }
    }
  }
}
