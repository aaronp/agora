package agora.rest.client

import agora.api.streams.BaseProcessor
import agora.rest.client
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import io.circe.Json

class AkkaWSClient[Mat](publisher: BaseProcessor[Json]) {

  def cancel() = {}

  def takeNext(n: Int) = {}

}

object AkkaWSClient {
  def apply[Mat](subscriber: ClientSubscription[Json])(implicit mat: Materializer): AkkaWSClient[Mat] = {

    val kitchen: Sink[Json, NotUsed] = Sink.fromSubscriber(new client.ClientSubscription.Facade[Json](subscriber))

    ???
  }

  def apply[Mat](flow: Flow[Message, Message, Mat])(implicit mat: Materializer): AkkaWSClient[Mat] = {
    new AkkaWSClient(flow)
  }
}
