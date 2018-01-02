package agora.rest.stream

import agora.api.streams.BaseProcessor
import agora.rest.client.{RestClient, StreamPublisherWebsocketClient, StreamSubscriberWebsocketClient}
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.{AkkaImplicits, ClientConfig}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import org.reactivestreams.Publisher

import scala.concurrent.Future

/**
  * A client to the Routes provided by [[StreamRoutes]]
  *
  * @param clientConf
  */
case class StreamRoutesClient(clientConf: ClientConfig) extends FailFastCirceSupport with StrictLogging {
  private lazy val clientSystem: AkkaImplicits = clientConf.newSystem()

  def location = clientConf.location

  lazy val restClient: RestClient = clientConf.clientFor(location)

  object subscriptions {
    def takeNext(name: String, n: Int) = {
      import clientSystem._
      takeNextVerb("subscribe", name, n).flatMap { resp =>
        Unmarshal(resp).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("subscribe", name)

    def list() = listVerb("subscribe")

    def createSubscriber(name: String, subscriber: BaseProcessor[Json] = BaseProcessor[Json](100)) = {
      val url = s"${location.asWebsocketURL}/rest/stream/subscribe/$name"
      openConnection(url, subscriber)
    }

    private def openConnection[T <: BaseProcessor[Json]](url: String, subscriber: T) = { //(implicit clientSystem: AkkaImplicits) = {
      import clientSystem._

      StreamSubscriberWebsocketClient.openConnection(url, subscriber).map {
        case (httpResp, client) =>
          logger.info(s"Connected websocket to $url: $httpResp")
          client
      }
    }
  }
  object publishers {
    def takeNext(name: String, n: Int) = {
      import clientSystem._

      takeNextVerb("publish", name, n).flatMap { resp =>
        Unmarshal(resp.entity).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("publish", name)

    def list() = listVerb("publish")

    def create(name: String, publisher: Publisher[Json]) = {
      import clientSystem._

      val url = s"${location.asWebsocketURL}/rest/stream/publish/$name"
      StreamPublisherWebsocketClient.openConnection(url, publisher)
    }
  }

  private def takeNextVerb(publishOrSubscribe : String, name: String, n: Int) = {
    val url = s"/rest/stream/$publishOrSubscribe/$name/request"
    restClient.send(HttpRequest(HttpMethods.GET, url))
  }

  private def cancelVerb(publishOrSubscribe : String, name: String) = {
    import clientSystem._
    val url = s"/rest/stream/$publishOrSubscribe/$name/cancel"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[ClientSubscriptionMessage].mapTo[Cancel]
    }
  }

  private def listVerb(publishOrSubscribe : String): Future[Set[String]] = {
    import clientSystem._
    val url = s"/rest/stream/$publishOrSubscribe"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[Set[String]]
    }
  }


}


object StreamRoutesClient extends StrictLogging {

}