package agora.rest.client

import agora.flow.{HasPublisher, _}
import agora.rest.stream.SocketPipeline
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{ExecutionContext, Future}

/**
  * contains the publishers/subscribers needed to setup a websocket message flow
  *
  * @param subscriber the subscriber to connect to the data coming from the websocket
  * @tparam S
  */
class StreamSubscriberWebsocketClient[NewQ[_], S <: Subscriber[Json]](val subscriber: S, newQueueArgs: NewQ[Json])(implicit ec: ExecutionContext)
    extends HasPublisher[Json]
    with StrictLogging { self =>

  val dataSubscriber: SocketPipeline.DataSubscriber[Json] = SocketPipeline.DataSubscriber[Json]()
  dataSubscriber.republishingDataConsumer.subscribe(subscriber)

  def flow = dataSubscriber.flow

  override protected def underlyingPublisher: Publisher[Json] = {
    dataSubscriber.republishingDataConsumer
  }
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
      //settings.withIdleTimeout(10.minutes)
      settings
    }

    val (respFuture, _) = httpExp.singleWebSocketRequest(WebSocketRequest(address), client.flow, settings = connSettings)
    respFuture.map { upgradeResp =>
      val status = upgradeResp.response.status
      logger.debug(s"Upgraded subscriber websocket w/ status $status for $address: ${upgradeResp.response}")
      client
    }
  }
}
