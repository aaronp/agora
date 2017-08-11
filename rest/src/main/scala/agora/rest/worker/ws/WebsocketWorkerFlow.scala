package agora.rest.worker.ws

import agora.rest.worker.ws.WebsocketWorker.logger
import akka.NotUsed
import akka.actor.ActorRef
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.syntax._
import org.slf4j.LoggerFactory

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}

/**
  * Represents the logic for handling a websocket-based worker dialogue.
  *
  */
trait WebsocketWorker {

  /**
    * In order to flatmap a Flow, we need an input -> Future[output] signature.
    *
    * That should suffice, as we have to handle:
    *
    * @param from
    * @return
    */
  def onMessage(from: FromWebsocketWorker): Future[List[ToWebsocketWorker]]

  def onMessageAsSource(from: FromWebsocketWorker): Source[ToWebsocketWorker, NotUsed] = {
    val future                                                = onMessage(from)
    val messagesSrc: Source[List[ToWebsocketWorker], NotUsed] = Source.fromFuture(future)
    val msgSrc: Source[ToWebsocketWorker, NotUsed]            = messagesSrc.mapConcat(identity)
    msgSrc
  }

  def flow(unmarshalTimeout: FiniteDuration)(implicit mat: Materializer): Flow[Message, TextMessage, NotUsed] = {

    def logger = LoggerFactory.getLogger(getClass)
    Flow[Message]
      .flatMapConcat {
        case inputMessage: TextMessage =>
          val requestBody: Future[String] = inputMessage.textStream.reduce(_ ++ _).runWith(Sink.head)
          val responseSrc: Source[ToWebsocketWorker, NotUsed] = Source.fromFuture(requestBody).flatMapConcat {
            case FromWebsocketWorker(msg) =>
              onMessageAsSource(msg)
            case other =>
              val msg = s"Couldn't interpret web socket text message '${other}'"
              logger.error(msg)
              Source.failed(new Exception(msg))
          }

          responseSrc.map { (resp: ToWebsocketWorker) =>
            TextMessage(resp.asJson.noSpaces)
          }
        case bm: BinaryMessage =>
          logger.error("Ignoring binary websocket message")
          bm.dataStream.runWith(Sink.ignore)
          Source.failed(new Exception("binary messages not supported"))
      }
  }
}

object WebsocketWorker extends StrictLogging {

  def apply(websocketWorkerActor: ActorRef): WebsocketWorker = {
    new ActorWebsocketWorkerClient(websocketWorkerActor)
  }

  class ActorWebsocketWorkerClient(actor: ActorRef) extends WebsocketWorker {
    override def onMessage(from: FromWebsocketWorker) = {
      val promise = Promise[List[ToWebsocketWorker]]()
      actor ! WebsocketWorkerActor.WebsocketWorkerRequest(from, promise)
      promise.future
    }
  }

}
