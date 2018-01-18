package agora.rest.stream

import java.util.UUID

import agora.BaseSpec
import agora.flow.ConsumerQueue.Unbounded
import agora.flow._
import agora.rest.client.StreamPublisherWebsocketClient
import agora.rest.{HasMaterializer, RunningService, ServerConfig}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually

class StreamRoutesIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterEach with Eventually with HasMaterializer {

  var serverConfig: ServerConfig                                 = null
  var runningService: RunningService[ServerConfig, StreamRoutes] = null

  implicit def asRichDataUploadSnapshot(snapshot: DataUploadSnapshot) = new {
    def consumerSnapshot: SubscriberSnapshot = {
      snapshot.dataConsumingSnapshot.subscribers.size shouldBe 1
      snapshot.dataConsumingSnapshot.subscribers.values.head
    }

    def controlSnapshot: SubscriberSnapshot = {
      snapshot.controlMessagesSnapshot.subscribers.size shouldBe 1
      snapshot.controlMessagesSnapshot.subscribers.values.head
    }
  }

  "StreamRoutes.publisher" should {

    def currentState: StreamRoutesState = runningService.service.state

    def uploadFlow(name: String): DataUploadFlow[AsConsumerQueue.QueueArgs, Json] = {
      currentState.getUploadEntrypoint(name).getOrElse(sys.error(s"No publisher was created for '$name'"))
    }

    def consumerFlows(name: String): List[DataConsumerFlow[Json]] = {
      currentState.getSimpleSubscriber(name).getOrElse(sys.error(s"No subscriber was created for '$name'"))
    }

    def singleConsumerFlow(name: String): DataConsumerFlow[Json] = {
      val all = consumerFlows(name)
      all.ensuring(_.size == 1, s"${all.size} subscribers found for '$name'").head
    }

    def uploadSnapshot(name: String): DataUploadSnapshot = uploadFlow(name).snapshot

    "publisher only request more elements when an explicit takeNext is sent" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      val name   = "publisherTakeNext" + System.currentTimeMillis()

      // 1) create a publisher to publish data
      client.publishers.list().futureValue shouldBe empty
      val msgSource: BaseProcessor[Json] = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, msgSource).futureValue
      currentState.subscriberKeys() shouldBe empty
      currentState.uploadKeys() should contain only (name)

      val snapshotBeforeOwtsPublished: DataUploadSnapshot = uploadSnapshot(name)

      snapshotBeforeOwtsPublished.name shouldBe name
      snapshotBeforeOwtsPublished.dataConsumingSnapshot.subscribers.size shouldBe 0
      val initialControlSnapshot = snapshotBeforeOwtsPublished.controlSnapshot

      initialControlSnapshot.totalPushed shouldBe 0

      val initiallyRequested = publisher.throttledPublisher.requested()
      withClue("Akka Http should be requesting elements from our throttled publisher, but we've not explicitly pulled any elements yet") {
        publisher.throttledPublisher.allowed() shouldBe 0
        initiallyRequested should be > 0L
      }
      withClue("...but the underlying published should not yet have had any elements requested") {
        msgSource.currentRequestedCount() shouldBe 0
      }

      // 2) publish our first data. At the moment our StreamPublisherWebsocketClient shouldn't be pulling any data
      // so the remote web service should NOT receive that data, and its control messages should be unchanged
      publisher.underlyingUserPublisher.publish(json"""{ "first" : "msg" }""")
      uploadSnapshot(name).controlSnapshot shouldBe initialControlSnapshot

      uploadSnapshot(name).dataConsumingSnapshot.subscribers shouldBe empty

      {
        // double-check by reading the snapshot via the REST snapshot route
        val Right(snapshotFromEndpoint) = client.snapshot(name).futureValue.as[DataUploadSnapshot]
        snapshotFromEndpoint.controlSnapshot shouldBe initialControlSnapshot
        snapshotFromEndpoint.dataConsumingSnapshot.subscribers shouldBe empty
      }

      // 3) finally, explicitly request some elements from the publisher -- typically this is done via a subscriber sending a remote
      // message, but we're just testing the publisher here, so we invoke it explicitly ourselves.
      withClue("as akka.http requested ~16 elements, and we've just allowed 2, the total we can take should be 2") {
        publisher.remoteWebServiceRequestingNext(2) shouldBe 2
      }

      // verify our locally throttled publisher has requested elements. The published 'first : msg' should now
      // have been pushed to the server, and an additional element (2 requested - 1 sent) should be pending
      withClue("the 2 allowed should have been passed through to the underlying subscription, so our 'allowed' should be back to zero") {
        publisher.throttledPublisher.allowed() shouldBe 0
      }
      publisher.throttledPublisher.requested() shouldBe initiallyRequested - 2
    }

    "pull data based on consumers" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      val name   = "pullTest" + System.currentTimeMillis()
      client.subscriptions.list().futureValue shouldBe empty
      currentState.subscriberKeys() shouldBe empty
      currentState.uploadKeys() shouldBe empty

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'name'
      val streamSubscriber   = client.subscriptions.createSubscriber(name, new ListSubscriber[Json]).futureValue
      val initialSubscribers = client.subscriptions.list().futureValue
      initialSubscribers shouldBe Set(name)
      currentState.subscriberKeys() shouldBe Set(name)
      currentState.uploadKeys() shouldBe empty
      streamSubscriber.subscriber.isSubscribed() shouldBe true

      // 1.1) when we request from our underlying subscriber, it should send a 'TakeNext' control
      // message to its corresponding DataConsumerFlow handler
      {
        streamSubscriber.subscriber.request(8)

        eventually {
          singleConsumerFlow(name).underlyingRepublisher.snapshot() shouldBe PublisherSnapshot(Map(1 -> SubscriberSnapshot(8, 0, 0, 8, 0, Unbounded)))
        }

      }

      // 2) now create a publisher to publish data to 'name'
      client.publishers.list().futureValue shouldBe empty
      val localPublisher = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, localPublisher).futureValue

      withClue("The underlying published should have the elements requested from our pre-existing subscriber") {
        eventually {
          singleConsumerFlow(name).underlyingRepublisher.snapshot() shouldBe PublisherSnapshot(Map(1 -> SubscriberSnapshot(8, 0, 0, 8, 0, Unbounded)))

          // the 'streamSubscriber.subscriber.request(8)' call from the local subscriber above should've gone:
          // from the StreamSubscriberWebsocketClient as a control flow 'TakeNext' message ...
          // to the DataConsumerFlow.controlMessageSubscriber, which translates the TakeNext message to ...
          // 'allowNext' from the DataConsumerFlow.throttledRepublisher
          //
          // the DataConsumerFlow.throttledRepublisher wraps an underlying processor which consumes from the
          // DataUploadFlow and pushes those messages to the DataConsumerFlow.flow
          localPublisher.currentRequestedCount() shouldBe 8
        }
      }

      // 3) have the consumer request some elements (though none have yet been published)
      // requesting elements from our wrapped subscriber should translate into a 'takeNext' call on
      // the StreamSubscriberWebsocketClient,
      //
      // so this should have the same effect as calling:
      //       streamSubscriber.takeNext(0)
      streamSubscriber.subscriber.request(1)

      // we should now have:
      // local subscriber -->
      // ... send 'take next' to server -->
      // ....... server sends 'takeNext' control message to local WS publisher ->
      // ........... local WS publisher 'takeNext' (request) from local msgSource  ->
      //
      withClue("The local subscriber should now have requested an element from the local publisher via the web service") {
        localPublisher.currentRequestedCount() shouldBe 8
      }

      // this client consumer 'request' (take next) request should go through our client, server, and back out at our local publisher
      publisher.underlyingUserPublisher.publish(json"""{ "gruess" : "gott" }""")

      eventually {
        streamSubscriber.subscriber.received() shouldBe List(json"""{ "gruess" : "gott" }""")
      }

      localPublisher.currentRequestedCount() shouldBe 7
    }
    "send data requested prior to attaching a publisher" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      val name = "sendDataPrior" + UUID.randomUUID()

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'name'
      val firstListener: ListSubscriber[Json] = new ListSubscriber[Json]
      val subscriber                          = client.subscriptions.createSubscriber(name, firstListener).futureValue
      val initialSubscribers                  = client.subscriptions.list().futureValue
      firstListener.isSubscribed() shouldBe true
      initialSubscribers shouldBe Set(name)

      // request some data before we even have a publisher...
      firstListener.request(4)

      // 2) now create a publisher to publish data to 'name'
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, msgSource).futureValue

      // 3) send some messages through
      withClue("we should be requesting 4 items already") {
        msgSource.currentRequestedCount() shouldBe 4
      }
    }

    "allow subsequent subscribers to listen to the attached subscriber" in {
      val client = StreamRoutesClient(serverConfig.clientConfig)
      client.subscriptions.list().futureValue shouldBe empty

      val name = "subsequentSubscribers" + UUID.randomUUID()

      // 1) create a subscriber -- someone to listen to the data we're going to publish against some named topic 'name'
      val firstListener: ListSubscriber[Json] = new ListSubscriber[Json]
      val subscriber                          = client.subscriptions.createSubscriber(name, firstListener).futureValue
      val initialSubscribers                  = client.subscriptions.list().futureValue
      firstListener.isSubscribed() shouldBe true
      initialSubscribers shouldBe Set(name)

      // 2) now create a publisher to publish data to 'name'
      client.publishers.list().futureValue shouldBe empty
      val msgSource = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, msgSource).futureValue

      // 3) send some messages through
      withClue("Nothing should yet be requesting items") {
        msgSource.currentRequestedCount() shouldBe 0
      }

      val secondListener = new ListSubscriber[Json]
      subscriber.subscribe(secondListener)
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
    serverConfig = ServerConfig("host=localhost", "port=7777", "akka.http.client.idle-timeout=9min")

    val streamRoutes = new StreamRoutes
    runningService = RunningService.start[ServerConfig, StreamRoutes](serverConfig, streamRoutes.routes, streamRoutes).futureValue
  }

  def stopAll() = {
    serverConfig.stop().futureValue
    runningService.stop().futureValue
  }
}
