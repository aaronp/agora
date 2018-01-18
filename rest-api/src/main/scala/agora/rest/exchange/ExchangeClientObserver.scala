package agora.rest.exchange

import agora.api.exchange.observer._
import agora.api.worker.HostLocation
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws._
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Sources codes for the websocket connection plumbing in order to connect a [[ExchangeObserver]] to a websocket
  * (supported by ExchangeObserverWebsocketRoutes on t'other end
  */
object ExchangeClientObserver extends StrictLogging {

  /** attached the supplied ExchangeObserver to an exchange running at location over a websocket.
    *
    * @param location the server location to which we want to connect
    * @param obs the observer to delegate the websocket messages to
    * @param http the http context used to create a websocket
    * @param ec the usual...
    * @param mat the usual...
    * @return a subscription which can be used to cancel the events. The 'request' part isn't required, as requesting more elements will be done automatically as they are received
    */
  def connectClient(location: HostLocation, obs: ExchangeObserver, http: HttpExt)(implicit ec: ExecutionContext, mat: Materializer): Future[Subscription] = {
    val clientFlow = ClientFlow(obs)

    val url                         = s"${location.asWebsocketURL}/rest/exchange/observe"
    val wsRequest: WebSocketRequest = WebSocketRequest(url)

    val pear: (Future[WebSocketUpgradeResponse], NotUsed) = http.singleWebSocketRequest(wsRequest, clientFlow.flow)
    pear._1.map {
      case ValidUpgrade(_, _) => clientFlow.subscription
      case InvalidUpgradeResponse(_, cause) =>
        val errorMsg = s"Couldn't upgrade to websockets using $url : $cause"
        logger.error(errorMsg, cause)
        throw new Exception(s"Couldn't upgrade to websockets using $url : $cause")
    }
  }

  /**
    * see https://doc.akka.io/docs/akka-http/current/scala/http/client-side/websocket-support.html
    *
    * the 'flow' constructed in this class can be used to construct a websocket client
    *
    * @param wrappedObserver the observer which will receive events from the constructed flow
    */
  case class ClientFlow(wrappedObserver: ExchangeObserver) {
    val notificationSink: Sink[ExchangeNotificationMessage, NotUsed] = {
      Sink.fromSubscriber(ExchangeSubscriber(wrappedObserver))
    }

    val clientPublisher = new ClientPublisher()

    def subscription: Subscription = clientPublisher

    val messageSink: Sink[Message, NotUsed] = notificationSink.contramap[Message] {
      case TextMessage.Strict(jsonString) =>
        val res = decode[ExchangeNotificationMessage](jsonString)
        res match {
          case Left(err) =>
            val errMsg = s"Error decoding a from $jsonString : $err "
            logger.error(errMsg, err)
            clientPublisher.notifyOfCancel()
            sys.error(errMsg)
          case Right(msg) =>
            logger.info(s"On $msg")
            clientPublisher.notifyOfRequest(1)
            msg
        }
      case other =>
        val errMsg = s"Expected strict text messages in the client websocket exchange, but encountered: $other"
        logger.error(errMsg)
        clientPublisher.notifyOfCancel()
        sys.error(errMsg)
    }

    val clientControlMessageSource: Source[Message, NotUsed] = {
      val baseSource: Source[ClientSubscriptionMessage, NotUsed] = Source.fromPublisher(clientPublisher)
      baseSource.map { msg =>
        import io.circe.syntax._
        val textMsg: Message = TextMessage(msg.asJson.noSpaces)
        textMsg
      }
    }

    /**
      * A flow which can be connected to a websocket client
      */
    val flow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSourceMat(messageSink, clientControlMessageSource)(Keep.right)
  }

  class ClientPublisher() extends Publisher[ClientSubscriptionMessage] with Subscription with StrictLogging {
    private var subscriberOpt: Option[Subscriber[_ >: ClientSubscriptionMessage]] = None

    override def subscribe(s: Subscriber[_ >: ClientSubscriptionMessage]) = {
      s.onSubscribe(this)
      logger.debug("ClientPublisher being subscribed to")
      require(subscriberOpt.isEmpty)
      subscriberOpt = Option(s)
    }

    /** Enqueue a cancel message to the server */
    def notifyOfCancel() = {
      logger.debug("ClientPublisher notifying of cancel")
      subscriberOpt.foreach(_.onNext(ClientSubscriptionMessage.cancel))
    }

    /** Enqueue a 'takeNext' message to the server */
    def notifyOfRequest(n: Long) = {
      logger.debug(s"ClientPublisher notifying of take $n")
      subscriberOpt.foreach(_.onNext(ClientSubscriptionMessage.takeNext(n)))
    }

    override def cancel() = {
      logger.debug("ClientPublisher being cancelled")
      // if this side wants to cancel
      notifyOfCancel()
    }

    override def request(n: Long) = {
      logger.debug(s"ClientPublisher being requested of $n")
      // we ignore the 'request' back-pressure, as the messages flowing through this publisher are control messages
      // themselves
    }
  }

}
