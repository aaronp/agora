package agora.rest.stream

import agora.api.streams.BaseProcessor
import agora.rest.client.{RestClient, StreamWebsocketClient}
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.{AkkaImplicits, ClientConfig}
import akka.http.scaladsl.model.{HttpMethod, HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json

import scala.concurrent.Future

/**
  * A client to the Routes provided by [[StreamRoutes]]
  *
  * @param clientConf
  */
case class StreamRoutesClient(clientConf: ClientConfig) {
  private lazy val clientSystem: AkkaImplicits = clientConf.newSystem()

  def location = clientConf.location

  lazy val restClient: RestClient = clientConf.clientFor(location)

  object subscriptions {
    def takeNext(name: String, n: Int) = {
      takeNextVerb("subscribe", name, n).flatMap { resp =>
        Unmarshal(resp.entity).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("subscribe", name)

    def list() = listVerb("subscribe")

    def createSubscriber(name: String, subscriber: BaseProcessor[Json] = BaseProcessor[Json](100)) = {
      val url = s"${location.asWebsocketURL}/rest/stream/subscribe/$name"
      openConnection(subscriber, url)
    }
  }
  object publishers {
    def takeNext(name: String, n: Int) = {
      takeNextVerb("publish", name, n).flatMap { resp =>
        Unmarshal(resp.entity).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("publish", name)

    def list() = listVerb("publish")

    def create(name: String, subscriber: BaseProcessor[Json]) = {
      val url = s"${location.asWebsocketURL}/rest/stream/publish/$name"
      openConnection(subscriber, url)
    }
  }

  private def takeNextVerb(publishOrSubscribe : String, name: String, n: Int) = {
    val url = s"/rest/stream/$publishOrSubscribe/$name/request"
    restClient.send(HttpRequest(HttpMethods.GET, url))
  }

  private def cancelVerb(publishOrSubscribe : String, name: String) = {
    val url = s"/rest/stream/$publishOrSubscribe/$name/cancel"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[ClientSubscriptionMessage].mapTo[Cancel]
    }
  }

  private def listVerb(publishOrSubscribe : String): Future[Set[String]] = {
    val url = s"/rest/stream/$publishOrSubscribe"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[Set[String]]
    }
  }


    def openConnection[T <: BaseProcessor[Json]](subscriber: T, url: String) = { //(implicit clientSystem: AkkaImplicits) = {
      import clientSystem._

      StreamWebsocketClient.openConnection(url, subscriber).map {
        case (httpResp, client) =>
          logger.info(s"Connected websocket to $url: $httpResp")
          client

      }
    }
}


object StreamRoutesClient extends StrictLogging {

}