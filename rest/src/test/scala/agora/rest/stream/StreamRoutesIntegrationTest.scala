package agora.rest.stream

import agora.BaseSpec
import agora.api.streams.{BaseProcessor, ListSubscriber}
import agora.rest.client.StreamPublisherWebsocketClient
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.{HasMaterializer, RunningService, ServerConfig}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.BeforeAndAfterEach

class StreamRoutesIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterEach with HasMaterializer {

  var serverConfig: ServerConfig                          = null
  var service: RunningService[ServerConfig, StreamRoutes] = null

  "StreamRoutes" should {
    "service websocket requests" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'dave'
      val subscriber         = client.subscriptions.createSubscriber("dave").futureValue
      val initialSubscribers = client.subscriptions.list().futureValue
      initialSubscribers shouldBe Set("dave")

      // 2) create a publisher
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]]("dave", msgSource).futureValue
      val publisherListener1 = new ListSubscriber[ClientSubscriptionMessage]
      val publisherListener2 = new ListSubscriber[ClientSubscriptionMessage]
      publisher.subscribe(publisherListener1)
      publisher.subscribe(publisherListener2)

      withClue("Nothing should yet be requesting items") {
        msgSource.currentRequestedCount() shouldBe 0
      }

    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    startAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    stopAll()
  }

  def startAll() = {
    serverConfig = ServerConfig("host=localhost", "port=7777")
    val sr = new StreamRoutes
    service = RunningService.start[ServerConfig, StreamRoutes](serverConfig, sr.routes, sr).futureValue
  }

  def stopAll() = {
    serverConfig.stop().futureValue
    service.stop().futureValue
  }
}
