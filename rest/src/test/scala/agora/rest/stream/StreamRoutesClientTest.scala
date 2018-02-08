package agora.rest.stream

import agora.BaseSpec
import agora.flow.{HistoricProcessor, HistoricProcessorDao, ListSubscriber}
import agora.rest.client.StreamPublisherWebsocketClient
import agora.rest.{RunningService, ServerConfig}
import com.typesafe.config.ConfigFactory
import io.circe.Json
import org.scalatest.concurrent.{Eventually, ScalaFutures}

import scala.concurrent.ExecutionContext
import scala.util.Try

class StreamRoutesClientTest extends BaseSpec with ScalaFutures with Eventually {
  "StreamRoutesClient" should {
    "be able to connect a subscriber to an existing publisher" in {

      // start a server which will host registered publishers/subscribers
      val server: RunningService[ServerConfig, StreamRoutes] = null // StreamRoutes.start().futureValue

      val serverConf = if (server == null) ServerConfig(ConfigFactory.load("agora-defaults.conf")) else server.conf
      // connect a client to the server
      val client: StreamRoutesClient = StreamRoutesClient(serverConf.clientConfig)

      try {
        // use the client to create a publisher (or could wrap an existing publisher)
        val dao : HistoricProcessorDao[String] = HistoricProcessorDao[String]()(ExecutionContext.global)
        val publisher = client.publishers.create[String]("example", dao).futureValue

        // use the client to create a listener which can republish the data locally (or could wrap an existing subscriber)
        val localListener = client.subscriptions.createSubscriber("example").futureValue

        // go round-trip -- publish some data and see it pop out on our listener (when requested)
        publisher.underlyingPublisher.onNext("Hello World")

        val list = new ListSubscriber[Json]()
        localListener.subscriber.subscribe(list)
        list.received() shouldBe Nil

        localListener.dataSubscriber.takeNext(2)

        publisher.underlyingPublisher.onNext("Anyone there?")


        eventually {
          list.received() shouldBe List("Hello World")
        }
      } finally {
        Try(server.stop().futureValue)
        client.close()
      }
    }
  }
}
