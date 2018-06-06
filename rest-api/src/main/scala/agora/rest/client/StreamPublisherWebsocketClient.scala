package agora.rest.client

import akka.NotUsed
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.ws.{Message, WebSocketRequest}
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import lupin.data.HasPublisher
import org.reactivestreams.Publisher

import scala.concurrent.{ExecutionContext, Future}

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
class StreamPublisherWebsocketClient[E: Encoder, P <: Publisher[E]](override val underlyingPublisher: P, bufferCapacity: Int = 50)(
    implicit ec: ExecutionContext)
    extends HasPublisher[E]
    with StrictLogging {

//  val pipeline = SocketPipeline.DataPublisher[E](underlyingPublisher)
  def flow    : Flow[Message, Message, NotUsed] = ??? //pipeline.flow
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
