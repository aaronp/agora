package agora.rest.stream

import agora.BaseSpec
import agora.api.streams.{BaseProcessor, ListSubscriber}
import agora.rest.client.{StreamPublisherWebsocketClient, StreamSubscriberWebsocketClient}
import agora.rest.{HasMaterializer, RunningService, ServerConfig}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.scalatest.BeforeAndAfterEach
import agora.api.streams.AsConsumerQueue._

class StreamRoutesIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterEach with HasMaterializer {

  var serverConfig: ServerConfig = null
  var service: RunningService[ServerConfig, StreamRoutes] = null

  "StreamRoutes" should {
    "pull data based on consumers" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'dave'
      val streamSubscriber = client.subscriptions.createSubscriber("dave", new ListSubscriber[Json]).futureValue
      val initialSubscribers = client.subscriptions.list().futureValue
      streamSubscriber.subscriber.isSubscribed() shouldBe true
      initialSubscribers shouldBe Set("dave")

      // 2) now create a publisher to publish data to 'dave'
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]]("dave", msgSource).futureValue

      // 3) send some messages through
      withClue("Nothing should yet be requesting items") {
        msgSource.currentRequestedCount() shouldBe 0
      }

      // this client consumer 'request' (take next) request should go through our client, server, and back out at our local publisher
      streamSubscriber.subscriber.request(3)

      msgSource.currentRequestedCount() shouldBe 3
    }
    "send data requested prior to attaching a publisher" ignore {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'dave'
      val firstListener: ListSubscriber[Json] = new ListSubscriber[Json]
      val subscriber = client.subscriptions.createSubscriber("dave", firstListener).futureValue
      val initialSubscribers = client.subscriptions.list().futureValue
      firstListener.isSubscribed() shouldBe true
      initialSubscribers shouldBe Set("dave")

      // request some data before we even have a publisher...
      firstListener.request(4)

      // 2) now create a publisher to publish data to 'dave'
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]]("dave", msgSource).futureValue


      // 3) send some messages through
      withClue("we should be requesting 4 items already") {
        msgSource.currentRequestedCount() shouldBe 4
      }

      ???
    }

    "allow subsequent subscribers to listen to the attached subscriber" ignore {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'dave'
      val firstListener: ListSubscriber[Json] = new ListSubscriber[Json]
      val subscriber = client.subscriptions.createSubscriber("dave", firstListener).futureValue
      val initialSubscribers = client.subscriptions.list().futureValue
      firstListener.isSubscribed() shouldBe true
      initialSubscribers shouldBe Set("dave")

      // 2) now create a publisher to publish data to 'dave'
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]]("dave", msgSource).futureValue


      // 3) send some messages through
      withClue("Nothing should yet be requesting items") {
        msgSource.currentRequestedCount() shouldBe 0
      }

      val secondListener = new ListSubscriber[Json]
      subscriber.subscribe(secondListener)

      ???
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
