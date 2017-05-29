package jabroni.exec.ws

import akka.NotUsed
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.scaladsl.Sink

/**
  * Execute w/ WebSockets
  *
  * see https://github.com/akka/akka-http/blob/v10.0.7/docs/src/test/scala/docs/http/scaladsl/server/WebSocketExampleSpec.scala
  *
  */
object ExecuteOverWS {

  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import akka.stream.scaladsl.{Source, Flow}
  import akka.http.scaladsl.Http
  import akka.http.scaladsl.model.ws.UpgradeToWebSocket
  import akka.http.scaladsl.model.ws.{TextMessage, Message}
  import akka.http.scaladsl.model.{HttpResponse, Uri, HttpRequest}
  import akka.http.scaladsl.model.HttpMethods._


  def apply(src: Source[String, Any]): Flow[Message, TextMessage, NotUsed] =
    Flow[Message]
      .mapConcat {
        // we match but don't actually consume the text message here,
        // rather we simply stream it back as the tail of the response
        // this means we might start sending the response even before the
        // end of the incoming message has been received
        case tm: TextMessage =>

          tm.textStream.runWith(Sink.ignore)
          TextMessage(src) :: Nil
        case bm: BinaryMessage =>
          // ignore binary messages but drain content to avoid the stream being clogged
          bm.dataStream.runWith(Sink.ignore)
          Nil
      }

}
