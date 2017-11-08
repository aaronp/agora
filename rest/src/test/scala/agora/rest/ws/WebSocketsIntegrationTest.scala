package agora.rest.ws

import akka.NotUsed
import akka.http.scaladsl.model.HttpMethods.POST
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, WSProbe}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{Graph, SourceShape}
import akka.util.ByteString
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.reflectiveCalls

/**
  * https://github.com/akka/akka-http/blob/master/docs/src/test/scala/docs/http/scaladsl/server/directives/WebSocketDirectivesExamplesSpec.scala
  *
  */
class WebSocketsIntegrationTest extends WordSpec with Matchers with Directives with ScalatestRouteTest { //BaseRoutesSpec {

  "A running websocket service should be able to handle messages" in {

    def greeter2(): Flow[Message, Message, Any] = {

      /**
        * Represents a websocket flow for request/responses
        */
      val concurrency = 3
      val foo: Flow[Message, Message, NotUsed] = Flow[Message].mapAsyncUnordered(concurrency) { msg =>
        val responses: Future[Message] = ???
        responses
      }

      /**
        * Represents a websocket flow for request -> response flow
        */
      def onSubscription(msg: Message): Source[Message, NotUsed] = {
        ???
      }

      val dave: Message => Graph[SourceShape[Message], NotUsed] = onSubscription _
      val bar                                                   = Flow[Message].flatMapMerge[Message, NotUsed](concurrency, dave)

      Flow[Message].mapConcat {
        case tm: TextMessage =>
          TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }
    }

    def greeter: Flow[Message, Message, Any] =
      Flow[Message].mapConcat {
        case tm: TextMessage =>
          TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }

    val wsRoute: Route = {
      post {
        path("testing") {
          extractRequest { req =>
            //          r.discardEntityBytes() // important to drain incoming HTTP Entity stream
            //          HttpResponse(404, entity = "Unknown resource!")
            handleWebSocketMessages(greeter)
          }
        }
      }
    }

    val wsClient = WSProbe()

    WS("/testing", wsClient.flow).withMethod(POST) ~> wsRoute ~> check {
      // check response for WS Upgrade headers
      isWebSocketUpgrade shouldEqual true

      // manually run a WS conversation
      wsClient.sendMessage("Peter")
      wsClient.expectMessage("Hello Peter!")

      wsClient.sendMessage(BinaryMessage(ByteString("abcdef")))
      wsClient.expectNoMessage(100.millis)

      wsClient.sendMessage("John")
      wsClient.expectMessage("Hello John!")

      wsClient.sendCompletion()
      wsClient.expectCompletion()
    }
  }
}
