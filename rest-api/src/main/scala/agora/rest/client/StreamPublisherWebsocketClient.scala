package agora.rest.client

import agora.flow.KeyedPublisher.KeyedPublisherSubscription
import agora.flow.{BaseProcessor, ConsumerQueue, ThrottledPublisher}
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

/** contains the publishers/subscribers needed to setup a websocket message flow.
  *
  * The flow should be like this:
  *
  * 1) start with some local publisher of data 'localPublisher'
  * 2) Add a 'StreamPublisherWebsocketClient' which will act as a subscriber to the 'localPublisher' which will
  * request elements as they are consumed from a remote web service
  * 3) when an element is explicitly requested (subscribed to) from the remote service, a control message will be
  * sent to this StreamPublisherWebsocketClient which will then in turn pull from the 'localPublisher'
  *
  */
class StreamPublisherWebsocketClient[E: Encoder, P <: Publisher[E]](val underlyingUserPublisher: P, bufferCapacity: Int = 50) extends StrictLogging {
  wsClient =>

  val throttledPublisher = new ThrottledPublisher[E](underlyingUserPublisher)
  // this source will call 'request' when a message is delivered
  private[client] val flowMessageSource: Source[Message, NotUsed] = Source.fromPublisher(throttledPublisher).map { event: E =>
    //  private[client] val flowMessageSource: Source[Message, NotUsed] = Source.fromPublisher(underlyingUserPublisher).map { event: E =>
    val json = event.asJson.noSpaces
    logger.debug(s"pushing message: $json")
    TextMessage(json)
  }

  // this subscribe to take/cancel messages coming from the remote service
  private val controlMessageProcessor: BaseProcessor[ClientSubscriptionMessage] = new BaseProcessor[ClientSubscriptionMessage] {
    override protected def onRequestNext(subscription: KeyedPublisherSubscription[SubscriberKey, ClientSubscriptionMessage], requested: Long): Long = {
      val x = super.onRequestNext(subscription, requested)
      logger.debug(s"the server's requested $requested ($x) control messages")
      x
    }

    override def newDefaultSubscriberQueue(): ConsumerQueue[ClientSubscriptionMessage] = ConsumerQueue.keepLatest(bufferCapacity)
  }
  controlMessageProcessor.request(1)

  def remoteWebServiceRequestingNext(n: Long): Long = {
    throttledPublisher.allowRequested(n)
  }

  def remoteWebServiceCancelling() = {
    throttledPublisher.cancel()
  }

  val flow: Flow[Message, Message, NotUsed] = {

    /*
     * These are the messages we'll receive from the server -- and as WE'RE publishing data to it, the server
     * will be sending 'TakeNext' or 'Cancel' messages.
     */
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(controlMessageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          logger.debug(s"received from remote web service : $jsonText")
          parse(jsonText) match {
            case Left(err) => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) =>
              json.as[ClientSubscriptionMessage] match {
                case Right(msg @ TakeNext(n)) =>
                  remoteWebServiceRequestingNext(n)
                  msg
                case Right(msg @ Cancel) =>
                  remoteWebServiceCancelling()
                  msg
                case Left(err) => sys.error(s"couldn't parse ${jsonText} as a client subscription message: $err")
              }
          }
        case other => sys.error(s"Expected a strict message but got " + other)
      }
    }

    Flow.fromSinkAndSource(kitchen, flowMessageSource)
  }
}

object StreamPublisherWebsocketClient extends StrictLogging {

  def bindPublisherToSocket[E: Encoder, T <: Publisher[E]](address: String, publisher: T)(implicit httpExp: HttpExt,
                                                                                          mat: Materializer): Future[StreamPublisherWebsocketClient[E, T]] = {
    import mat.executionContext

    val client          = new StreamPublisherWebsocketClient[E, T](publisher)
    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow)
    respFuture.map { upgradeResp =>
      val status = upgradeResp.response.status
      logger.debug(s"Upgraded publisher websocket w/ status $status for $address: ${upgradeResp.response}")
      client
    }
  }
}
