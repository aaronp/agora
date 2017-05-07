package jabroni.rest.exchange

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.{Marshalling, PredefinedToRequestMarshallers, ToEntityMarshaller, ToRequestMarshaller}
import akka.http.scaladsl.model.{HttpResponse, MessageEntity}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange._
import jabroni.api.worker.{HostLocation, WorkerDetails, WorkerRedirectCoords}
import jabroni.rest.client.RestClient
import jabroni.rest.worker.WorkerClient

import scala.concurrent.Future

class ExchangeClient(val rest: RestClient)(implicit sys : ActorSystem, mat: Materializer) extends Exchange with QueueObserver with RoutingClient with  FailFastCirceSupport {

  type JobResponse = Future[_ <: ClientResponse]

  import RestClient.implicits._

  implicit protected def execContext = mat.executionContext

  override def subscribe(request: WorkSubscription) = rest.send(ExchangeHttp(request)).flatMap(_.as[WorkSubscriptionAck])

  override def take(request: RequestWork) = rest.send(ExchangeHttp(request)).flatMap(_.as[RequestWorkAck])

  override def submit(submit: SubmitJob) = sendAndRoute(submit)._1

  private var workerClientByLocation = Map[HostLocation, WorkerClient]()

  def clientFor(location: HostLocation): WorkerClient = {
    workerClientByLocation.get(location) match {
      case Some(client) => client
      case None =>
        val newClient = WorkerClient(location)
        workerClientByLocation = workerClientByLocation.updated(location, newClient)
        newClient
    }
  }

  override def listJobs(request: QueuedJobs) = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[QueuedJobsResponse])
  }

  override def listSubscriptions(request: ListSubscriptions) = {
    rest.send(ExchangeHttp(request)).flatMap(_.as[ListSubscriptionsResponse])
  }
}

object ExchangeClient {
  def apply(rest: RestClient)(implicit mat: Materializer): Exchange with QueueObserver = new ExchangeClient(rest)

}