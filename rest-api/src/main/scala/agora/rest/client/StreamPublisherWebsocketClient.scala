package agora.rest.client

import agora.api.streams.{BaseProcessor, ConsumerQueue, HasPublisher}
import agora.rest.exchange.{Cancel, ClientSubscriptionMessage, TakeNext}
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import io.circe.parser._
import io.circe.syntax._
import org.reactivestreams.Publisher

import scala.concurrent.Future

/** contains the publishers/subscribers needed to setup a websocket message flow
  *
  */
private[client] class StreamPublisherWebsocketClient[E: Encoder, P <: Publisher[E]](val publisher: P, bufferCapacity: Int = 50) extends StrictLogging with HasPublisher[ClientSubscriptionMessage] {
  wsClient =>

  // we use this as a publisher so we can more directly control the request (take) /cancel messages
  private[client] val pullsFromPublisher = BaseProcessor.withMaxCapacity[E](bufferCapacity)
  private[client] val pullsFromPFP = BaseProcessor.withMaxCapacity[E](bufferCapacity)
  publisher.subscribe(pullsFromPublisher)
  pullsFromPublisher.subscribe(pullsFromPFP)

  // this subscribe to take/cancel messages coming from the remote service
  private[client]  val controlMessageProcessor: BaseProcessor[ClientSubscriptionMessage] = new BaseProcessor[ClientSubscriptionMessage] {
    override def newDefaultSubscriberQueue(): ConsumerQueue[ClientSubscriptionMessage] = ConsumerQueue.keepLatest(bufferCapacity)
  }

  override protected def underlyingPublisher: Publisher[ClientSubscriptionMessage] = controlMessageProcessor

  def takeNext(n: Long) = {
    logger.debug(s"Taking next $n...")
    pullsFromPublisher.request(n)
  }

  def cancel() = {
    logger.debug("Cancelling...")
    pullsFromPublisher.cancel()
  }

  val flow: Flow[Message, Message, NotUsed] = {
    // this source will call 'request' when a message is delivered
    val msgSrc: Source[Message, NotUsed] = Source.fromPublisher(pullsFromPFP).map { event: E =>
      val json = event.asJson.noSpaces
      logger.debug(s"sending control message: $json")
      TextMessage(json)
    }

    /*
     * These are the messages we'll receive from the server -- and as WE'RE publishing data to it, the server
     * will be sending 'TakeNext' or 'Cancel' messages.
     */
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(controlMessageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          logger.debug(s"received : $jsonText")
          parse(jsonText) match {
            case Left(err) => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) =>
              json.as[ClientSubscriptionMessage] match {
                case Right(msg@TakeNext(n)) =>
                  takeNext(n)
                  msg
                case Right(msg@Cancel) =>
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
}

object StreamPublisherWebsocketClient extends StrictLogging {

  def bindPublisherToSocket[E: Encoder, T <: Publisher[E]](address: String, publisher: T)(implicit httpExp: HttpExt, mat: Materializer): Future[StreamPublisherWebsocketClient[E, T]] = {
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


