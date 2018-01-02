package agora.rest.stream

import agora.api.streams.BaseProcessor
import agora.rest.client.{RestClient, StreamPublisherWebsocketClient, StreamSubscriberWebsocketClient}
import agora.rest.exchange.ClientSubscriptionMessage
import agora.rest.{AkkaImplicits, ClientConfig}
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}
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

    import clientSystem._

    def takeNext(name: String, n: Int) = {
      takeNextVerb("subscribe", name, n).flatMap { resp =>
        Unmarshal(resp).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("subscribe", name)

    def list() = listVerb("subscribe")

    def createSubscriber(name: String,
                         subscriber: BaseProcessor[Json] = BaseProcessor[Json](100),
                         maxCapacity: Option[Int] = None,
                         initialRequest: Option[Int] = None,
                         discardOverCapacity: Option[Boolean] = None
                        ) = {
      val queryString = {
        val options: List[String] = maxCapacity.map(v => s"maxCapacity=$v").toList ++
          initialRequest.map(v => s"initialRequest=$v").toList ++
          discardOverCapacity.map(v => s"discardOverCapacity=$v").toList
        options match {
          case Nil => ""
          case list => list.mkString("?", "&", "")
        }
      }
      val url = s"${location.asWebsocketURL}/rest/stream/subscribe/$name$queryString"
      openConnection(url, subscriber)
    }

    private def openConnection[T <: BaseProcessor[Json]](url: String, subscriber: T) = {
      StreamSubscriberWebsocketClient.openConnection(url, subscriber)
    }
  }

  object publishers {

    import clientSystem._

    def takeNext(name: String, n: Int) = {
      takeNextVerb("publish", name, n).flatMap { resp =>
        Unmarshal(resp.entity).to[Int]
      }
    }

    def cancel(name: String) = cancelVerb("publish", name)

    def list() = listVerb("publish")

    def create[E: Encoder, T <: Publisher[E]](name: String, publisher: T): Future[StreamPublisherWebsocketClient[E, T]] = {
      val url = s"${location.asWebsocketURL}/rest/stream/publish/$name"
      StreamPublisherWebsocketClient.openConnection(url, publisher)
    }
  }

  private def takeNextVerb(publishOrSubscribe: String, name: String, n: Int) = {
    val url = s"/rest/stream/$publishOrSubscribe/$name/request"
    restClient.send(HttpRequest(HttpMethods.GET, url))
  }

  private def cancelVerb(publishOrSubscribe: String, name: String) = {
    import clientSystem._
    val url = s"/rest/stream/$publishOrSubscribe/$name/cancel"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[ClientSubscriptionMessage].mapTo[Cancel]
    }
  }

  private def listVerb(publishOrSubscribe: String): Future[Set[String]] = {
    import clientSystem._
    val url = s"/rest/stream/$publishOrSubscribe"
    restClient.send(HttpRequest(HttpMethods.GET, url)).flatMap { resp =>
      Unmarshal(resp.entity).to[Set[String]]
    }
  }
}