package agora.rest.client

import agora.flow.BasePublisher.BasePublisherSubscription
import agora.flow.{HasPublisher, _}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, TextMessage, WebSocketRequest}
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.parser._
import io.circe.syntax._
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * contains the publishers/subscribers needed to setup a websocket message flow
  *
  * @param subscriber the subscriber to connect to the data coming from the websocket
  * @tparam S
  */
class StreamSubscriberWebsocketClient[NewQ[_], S <: Subscriber[Json]](val subscriber: S, newQueueArgs: NewQ[Json])(implicit asQueue: AsConsumerQueue[NewQ])
    extends HasPublisher[Json]
    with StrictLogging {
  self =>

  // when we request/cancel our subscriptions, we end up sending a message upstream to take/cancel
  private val controlMessagePublisher: BaseProcessor[ClientSubscriptionMessage] = BaseProcessor(None)

  /** Convenience method to explicitly cancel outside of the subscription
    */
  def cancel() = {
    controlMessagePublisher.publish(ClientSubscriptionMessage.cancel)
  }

  /** Convenience method to explicitly request more work items outside of the subscription
    */
  def takeNext(n: Long = 1) = {
    logger.debug(s"Stream Client taking $n")
    controlMessagePublisher.publish(ClientSubscriptionMessage.takeNext(n))
  }

  // this will be a subscriber of upstream messages which republishes the messages to the subscriber
  private val messageProcessor: BaseProcessor[Json] = new BaseProcessor[Json] {
    override def remove(id: Int): Unit = {
      self.cancel()
      super.remove(id)
    }

    override protected def onRequestNext(subscription: BasePublisherSubscription[Json], requested: Long): Long = {
      val nrReq = super.onRequestNext(subscription, requested)
      // we've requested 'nrReq', so ask upstream for nrReq
      self.takeNext(nrReq)
      nrReq
    }

    override def newDefaultSubscriberQueue(): ConsumerQueue[Json] = {
      asQueue.newQueue(newQueueArgs)
    }
  }
  messageProcessor.subscribe(subscriber)

  val flow: Flow[Message, Message, NotUsed] = {
    val controlMessagePublisherSrc: Source[Message, NotUsed] = Source.fromPublisher(controlMessagePublisher).map { msg =>
      val json = msg.asJson.noSpaces
      TextMessage(json)
    }
    val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(messageProcessor).contramap { msg: Message =>
      msg match {
        case TextMessage.Strict(jsonText) =>
          logger.debug(s"received : $jsonText")
          parse(jsonText) match {
            case Left(err)   => sys.error(s"couldn't parse ${jsonText} : $err")
            case Right(json) => json
          }
        case other => sys.error(s"Expected a strict message but got " + other)
      }
    }

    Flow.fromSinkAndSource(kitchen, controlMessagePublisherSrc)
  }

  override protected def underlyingPublisher: Publisher[Json] = messageProcessor
}

object StreamSubscriberWebsocketClient extends StrictLogging {
  def openConnection[NewQ[_], S <: Subscriber[Json]](address: String, subscriber: S, newQueueArgs: NewQ[Json])(
      implicit httpExp: HttpExt,
      mat: Materializer,
      asQ: AsConsumerQueue[NewQ]): Future[StreamSubscriberWebsocketClient[NewQ, S]] = {
    import mat.executionContext

    val client = new StreamSubscriberWebsocketClient(subscriber, newQueueArgs)

    val connSettings = {
      val settings = ClientConnectionSettings(httpExp.system)

      //
      // TODO - deleteme
      val x = settings.idleTimeout
      println(x)
      //
      // TODO - deleteme
      //

      settings.withIdleTimeout(10.minutes)
    }

    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow, settings = connSettings)
    respFuture.map { upgradeResp =>
      val status = upgradeResp.response.status
      logger.debug(s"Upgraded subscriber websocket w/ status $status for $address: ${upgradeResp.response}")
      client
    }
  }
}
