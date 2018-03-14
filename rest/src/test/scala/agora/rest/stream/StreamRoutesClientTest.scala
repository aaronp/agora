package agora.rest.stream

import agora.BaseRestSpec
import agora.flow.{DurableProcessor, DurableProcessorDao, ListSubscriber}
import agora.rest.client.StreamPublisherWebsocketClient
import agora.rest.{AkkaImplicits, RunningService, ServerConfig}
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.Future
import scala.util.Try

class StreamRoutesClientTest extends BaseRestSpec with ScalaFutures with Eventually {
  "StreamRoutesClient" should {
    "be able to connect a subscriber to an existing publisher" in {

      // start a server which will host registered publishers/subscribers
      val server: RunningService[ServerConfig, StreamRoutes] = StreamRoutes.start().futureValue

      val serverConf: ServerConfig = if (server == null) ServerConfig(ConfigFactory.load("agora-defaults.conf")) else server.conf
      // connect a client to the server
      import serverConf.serverImplicits._
      val client: StreamRoutesClient = StreamRoutesClient(serverConf.clientConfig)

      try {
        // use the client to create a publisher (or could wrap an existing publisher)
        val dao: DurableProcessorDao[String]                                            = DurableProcessorDao[String]()
        val publisher: StreamPublisherWebsocketClient[String, DurableProcessor[String]] = client.publishers.create[String]("example", dao).futureValue

        // use the client to create a listener which can republish the data locally (or could wrap an existing subscriber)
        val ffs           = DurableProcessor[Json]()
        val localListener = client.subscriptions.createSubscriber("example", ffs).futureValue
        localListener.subscriber.processorSubscription().get.request(1)

        // go round-trip -- publish some data and see it pop out on our listener (when requested)
        publisher.underlyingPublisher.onNext("Hello World")

        val list = new ListSubscriber[Json]()
        localListener.subscriber.subscribe(list)
        list.received() shouldBe Nil
        list.request(1)

        publisher.underlyingPublisher.onNext("Anyone there?")

        eventually {
          list.received() shouldBe List(Json.fromString("Hello World"))
        }
      } finally {
        Try(server.stop().futureValue)
        client.stop().futureValue
        serverConf.stop().futureValue
      }
    }
  }
}
