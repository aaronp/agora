package agora.rest.stream

import java.util.UUID

import agora.BaseSpec
import agora.flow.{PublisherSnapshot, _}
import agora.rest.client.StreamPublisherWebsocketClient
import agora.rest.stream.SocketPipeline._
import agora.rest.{HasMaterializer, RunningService, ServerConfig}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen}

import scala.concurrent.ExecutionContext

class StreamRoutesIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterEach with Eventually with GivenWhenThen with HasMaterializer {

  import StreamRoutesIntegrationTest._

  var serverConfig: ServerConfig                                 = null
  var runningService: RunningService[ServerConfig, StreamRoutes] = null
  var client: StreamRoutesClient                                 = null

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

  def currentState: StreamRoutesState = runningService.service.state

  def serverSideDataPublisher(name: String): DataSubscriber[Json] = {
    currentState.getUploadEntrypoint(name).getOrElse(sys.error(s"No publisher was created for '$name'"))
  }

  def consumerFlows(name: String) = {
    currentState.getSimpleSubscriber(name).getOrElse(sys.error(s"No subscriber was created for '$name'"))
  }

  def singleConsumerFlow(name: String) = {
    val all = consumerFlows(name)
    all.ensuring(_.size == 1, s"${all.size} subscribers found for '$name'").head
  }

  "StreamRoutes" should {

    "propagate 'takeNext' requests when publishers are created before subscribers" in {

      Given("a local publisher, connected to the server via 'client.publishers.create'")
      object LocalPublisher extends SimpleStringPublisher
      val name = "publisherFirst"
      client.publishers.create[String, LocalPublisher.type](name, LocalPublisher).futureValue

      When("local subscriber connects via the server (but does not yet request any elements)")
      val localSubscriber  = new ListSubscriber[Json]()
      val subscriberClient = client.subscriptions.createSubscriber(name, localSubscriber).futureValue

      Then("Nothing should be requested from the publisher, nor received from the subscriber")
      LocalPublisher.requestCalls shouldBe Nil
      localSubscriber.received() shouldBe Nil

      When("the subscriber then requests some elements")
      localSubscriber.request(7)

      Then("The publisher should get those requests")
      eventually {
        LocalPublisher.requestCalls shouldBe List(7)
      }

      When("the publisher then sends some data")
      LocalPublisher.subscriber.onNext("first data")

      Then("The subscriber should get receive it")
      eventually {
        localSubscriber.received() shouldBe List(Json.fromString("first data"))
      }
    }

    "propagate 'takeNext' requests again" in {

      Given("a local publisher, connected to the server via 'client.publishers.create'")
      object LocalPublisher extends SimpleStringPublisher
      val name = "publisherFirst"
      client.publishers.create[String, LocalPublisher.type](name, LocalPublisher).futureValue

      When("local subscriber connects via the server (but does not yet request any elements)")
      val localSubscriber  = new ListSubscriber[Json]()
      val subscriberClient = client.subscriptions.createSubscriber(name, localSubscriber).futureValue

      Then("Nothing should be requested from the publisher, nor received from the subscriber")
      LocalPublisher.requestCalls shouldBe Nil
      localSubscriber.received() shouldBe Nil

      When("the subscriber then requests some elements")
      localSubscriber.request(7)

      Then("The publisher should get those requests")
      eventually {
        LocalPublisher.requestCalls shouldBe List(7)
      }

      When("the publisher then sends some data")
      LocalPublisher.subscriber.onNext("first data")

      Then("The subscriber should get receive it")
      eventually {
        localSubscriber.received() shouldBe List(Json.fromString("first data"))
      }
    }
  }

  "StreamRoutes.publisher" ignore {

    "publisher only request more elements when an explicit takeNext is sent" in {
      val name = "publisherTakeNext" + System.currentTimeMillis()

      // 1) create a publisher to publish data
      client.publishers.list().futureValue shouldBe empty
      val msgSource: BaseProcessor[Json] = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, msgSource).futureValue
      currentState.subscriberKeys() shouldBe empty
      currentState.uploadKeys() should contain only (name)

    }

    "pull data based on consumers" in {
      val name = "pullTest" + System.currentTimeMillis()
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

      def anonymousSnapshots(pubSnap: PublisherSnapshot[_]) = {
        pubSnap.subscribers.mapValues(_.copy(name = ""))
      }

      // 1.1) when we request from our underlying subscriber, it should send a 'TakeNext' control
      // message to its corresponding DataConsumerFlow handler
      {
        streamSubscriber.subscriber.request(8)

      }

      // 2) now create a publisher to publish data to 'name'
      client.publishers.list().futureValue shouldBe empty
      val localPublisher = BaseProcessor.withMaxCapacity[Json](10)

      val publisher: StreamPublisherWebsocketClient[Json, BaseProcessor[Json]] =
        client.publishers.create[Json, BaseProcessor[Json]](name, localPublisher).futureValue

      withClue("The underlying published should have the elements requested from our pre-existing subscriber") {
        eventually {
          //          anonymousSnapshots(singleConsumerFlow(name).republishingDataConsumer.snapshot()) shouldBe PublisherSnapshot(Map(1 -> SubscriberSnapshot("", 8, 0, 0, 8, 0, Unbounded)))

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
      publisher.underlyingPublisher.publish(json"""{ "gruess" : "gott" }""")

      eventually {
        streamSubscriber.subscriber.received() shouldBe List(json"""{ "gruess" : "gott" }""")
      }

      localPublisher.currentRequestedCount() shouldBe 7
    }
    "send data requested prior to attaching a publisher" in {
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

    client = StreamRoutesClient(serverConfig.clientConfig)
  }

  def stopAll() = {
    serverConfig.stop().futureValue
    runningService.stop().futureValue
    client.stop().futureValue
  }

  //  trait SimpleStringPublisher extends StreamRoutesIntegrationTest.SimpleStringPublisher

  import concurrent.duration._

  override implicit def testTimeout: FiniteDuration = 10.seconds
}

object StreamRoutesIntegrationTest {

  trait SimpleStringPublisher extends Publisher[String] with Subscription {
    @volatile var cancelCalls                         = 0
    @volatile var requestCalls                        = List[Long]()
    @volatile var subscriber: Subscriber[_ >: String] = null

    override def subscribe(s: Subscriber[_ >: String]): Unit = {
      subscriber = s
      s.onSubscribe(this)
    }

    override def cancel(): Unit = this.synchronized {
      cancelCalls = cancelCalls + 1
    }

    override def request(n: Long): Unit = this.synchronized {
      requestCalls = n :: requestCalls
    }
  }

}
