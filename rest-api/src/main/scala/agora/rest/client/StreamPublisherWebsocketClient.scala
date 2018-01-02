package agora.rest.client

import agora.api.streams.{BaseProcessor, ConsumerQueue, HasPublisher}
import agora.rest.exchange.{Cancel, ClientSubscriptionMessage, TakeNext}
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import org.reactivestreams.Publisher

import scala.concurrent.Future

/** contains the publishers/subscribers needed to setup a websocket message flow
  *
  */
class StreamPublisherWebsocketClient[E: Encoder, P <: Publisher[E]](val publisher: P) extends StrictLogging with HasPublisher[ClientSubscriptionMessage] {
  wsClient =>

  private val clientMessagePublisher = BaseProcessor[E](10)
  publisher.subscribe(clientMessagePublisher)

  // this will be a subscriber of upstream messages and local (re-)publisher
  private val messageProcessor: BaseProcessor[ClientSubscriptionMessage] = new BaseProcessor[ClientSubscriptionMessage] {
    override def newDefaultSubscriberQueue(): ConsumerQueue[ClientSubscriptionMessage] = ConsumerQueue.keepLatest(100)
  }

  def takeNext(n: Long) = clientMessagePublisher.request(n)

  def cancel() = clientMessagePublisher.cancel()

  val flow: Flow[Message, Message, NotUsed] = {
    val msgSrc: Source[Message, NotUsed] = Source.fromPublisher(clientMessagePublisher).map { event: E =>
      val json = event.asJson.noSpaces
      logger.debug(s"sending control message: $json")
      TextMessage(json)
    }
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(messageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          logger.debug(s"received : $jsonText")
          parse(jsonText) match {
            case Left(err) => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) =>
              json.as[ClientSubscriptionMessage] match {
                case Right(msg @ TakeNext(n)) =>
                  takeNext(n)
                  msg
                case Right(msg @ Cancel) =>
                  cancel()
                  msg
                case Left(err) => sys.error(s"couldn't parse ${jsonText} as a client subscription message: $err")
              }
          }
        case other => sys.error(s"Expected a strict message but got " + other)
      }
    }

    Flow.fromSinkAndSource(kitchen, msgSrc)
  }

  override protected def underlyingPublisher  = messageProcessor

}

object StreamPublisherWebsocketClient extends StrictLogging {

  def openConnection[E: Encoder, T <: Publisher[E]](address: String, publisher: T)(implicit httpExp: HttpExt, mat: Materializer): Future[StreamPublisherWebsocketClient[E, T]] = {
    import mat.executionContext

    val client = new StreamPublisherWebsocketClient[E, T](publisher)
    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow)
    respFuture.map { upgradeResp =>
      val status = upgradeResp.response.status
      logger.debug(s"Upgraded publisher websocket w/ status $status for $address: ${upgradeResp.response}")
      client
    }
  }
}


