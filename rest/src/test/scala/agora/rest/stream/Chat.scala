package agora.rest.stream

import agora.flow.{BasePublisher, BaseSubscriber}
import agora.rest.ui.UIRoutes
import agora.rest.{RunningService, ServerConfig}
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{extractExecutionContext, extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._
import org.reactivestreams.Subscription

// an example of 2 different request types
case class ChatRequest(from: String, msg: String)

case class ChatNotification(from: String, msg: String)

class ChatServer {

  val publisher = BasePublisher[ChatNotification](100)

  def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    val subscriber = new BaseSubscriber[ChatRequest] {
      override def onNext(t: ChatRequest) = {
        publisher.publish(ChatNotification(t.from, t.msg))
        request(1)
      }
    }
    MessageFlow(publisher, subscriber)
  }

}

object Chat extends App with StrictLogging {
  val conf   = ServerConfig(args)
  val server = new ChatServer

  def chatRoutes: Route = {
    path("chat") {
      extractMaterializer { implicit materializer =>
        extractExecutionContext { implicit ec =>
          logger.debug("starting chat")
          handleWebSocketMessages(server.flow)
        }
      }
    }
  }

  val routes: Route = UIRoutes.unapply(conf).fold(chatRoutes)(_.routes ~ chatRoutes)

  RunningService.start(conf, routes, server)

}
