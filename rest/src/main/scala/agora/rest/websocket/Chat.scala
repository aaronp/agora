package agora.rest.websocket

import agora.api.streams.BasePublisher
import akka.stream.Materializer

// an example of 2 different request types
case class ChatRequest(from: String, msg: String)

case class ChatNotification(from: String, msg: String)

class ChatServer {

  val publisher = BasePublisher[ChatNotification]()

  private var messages = List[String]()

  def flow(implicit mat: Materializer) = {
    //          val flow: Flow[Message, Message, NotUsed] = MessageFlow()
  }

}
