package streaming.vertx

import java.util.concurrent.{CountDownLatch, TimeUnit}

import com.typesafe.scalalogging.StrictLogging
import io.vertx.lang.scala.ScalaVerticle
import monix.execution.Cancelable
import monix.reactive.Observable
import streaming.api._
import streaming.vertx.client.Client
import streaming.vertx.server.{Server, ServerEndpoint}

import scala.collection.mutable.ListBuffer

class ClientServerIntegrationTest extends BaseStreamingApiSpec with StrictLogging {

  "Server.start / Client.connect" should {
    "notify the server when the client completes" in {

      val port = 1235

      val messagesReceivedByTheServer = ListBuffer[String]()
      val messagesReceivedByTheClient = ListBuffer[String]()
      var serverReceivedOnComplete = false

      def chat(endpoint: Endpoint[WebFrame, WebFrame]): Cancelable = {

        val replies: Observable[String] = endpoint.fromRemote.doOnComplete { () =>
          logger.debug("from remote on complete")
          serverReceivedOnComplete = true
        }.map { frame =>
          logger.debug(s"To Remote : " + frame)
          val text = frame.asText.getOrElse("Received a non-text frame")
          messagesReceivedByTheServer += text
          s"echo: $text"
        }
        val all = "Hi - you're connected to an echo-bot" +: replies
        all.map[WebFrame](WebFrame.text).subscribe(endpoint.toRemote)
      }

      val started: ScalaVerticle = Server.start(port)(chat)
      var c: Client = null
      try {

        val gotFive = new CountDownLatch(5)
        c = Client.connect(EndpointCoords(port, "/some/path")) { endpoint =>
          endpoint.toRemote.onNext(WebFrame.text("from client"))
          endpoint.fromRemote.zipWithIndex.foreach {
            case (frame, i) =>
              logger.debug(s"$i Client got : " + frame)
              messagesReceivedByTheClient += frame.asText.getOrElse("non-text message received from server")
              gotFive.countDown()
              endpoint.toRemote.onNext(WebFrame.text(s"client sending: $i"))
              if (gotFive.getCount == 0) {
                logger.debug("completing client...")
                endpoint.toRemote.onComplete()
              }
          }
        }

        gotFive.await(testTimeout.toMillis, TimeUnit.MILLISECONDS) shouldBe true
        eventually {
          serverReceivedOnComplete shouldBe true
        }

        eventually {
          messagesReceivedByTheClient should contain inOrder ("Hi - you're connected to an echo-bot",
            "echo: from client",
            "echo: client sending: 0",
            "echo: client sending: 1",
            "echo: client sending: 2",
            "echo: client sending: 3")
        }

        messagesReceivedByTheServer should contain inOrder("from client",
          "client sending: 0",
          "client sending: 1",
          "client sending: 2",
          "client sending: 3")

      } finally {
        started.stop()
        if (c != null) {
          c.stop()
        }
      }

    }
    "connect to a server" in {

      val port = 1234

      val receivedFromServer = new CountDownLatch(1)
      var fromServer = ""
      val receivedFromClient = new CountDownLatch(1)
      var fromClient = ""

      // start the server
      val started: ScalaVerticle = Server.start(port) { endpoint: ServerEndpoint =>

        endpoint.toRemote.onNext(WebFrame.text(s"hello from the server at ${endpoint.socket.path}"))

        endpoint.fromRemote.foreach { msg: WebFrame =>
          msg.asText.foreach(fromClient = _)
          receivedFromClient.countDown()
        }
      }

      val c: Client = Client.connect(EndpointCoords(port, "/some/path")) { endpoint =>
        endpoint.fromRemote.foreach { msg =>
          msg.asText.foreach(fromServer = _)
          receivedFromServer.countDown()
        }
        endpoint.toRemote.onNext(WebFrame.text("from the client"))
      }

      receivedFromServer.await(testTimeout.toMillis, TimeUnit.MILLISECONDS)
      receivedFromClient.await(testTimeout.toMillis, TimeUnit.MILLISECONDS)

      fromServer shouldBe "hello from the server at /some/path"
      fromClient shouldBe "from the client"

      c.stop()
      started.stop()
    }
  }
}