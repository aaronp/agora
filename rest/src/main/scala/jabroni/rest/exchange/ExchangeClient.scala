package jabroni.rest.exchange

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange._
import jabroni.api.worker.{HostLocation, WorkerDetails}
import jabroni.rest.client.RestClient
import jabroni.rest.worker.WorkerClient

import scala.concurrent.Future


/**
  * Represents a something that will request work and get a response.
  *
  * A normal workflow would be to request work from an exchange, have that work eventually
  * matched with a worker, and then receive a 307 response, telling us where to go.
  *
  * We then make a request (typically the original one, but perhaps not if it was e.g. a multipart request w/
  * a large upload or summat) to that worker.
  *
  * @param rest
  * @param actorSystem
  * @param mat
  */
class ExchangeClient(val rest: RestClient)(implicit val actorSystem: ActorSystem, mat: Materializer)
  extends Exchange
    with QueueObserver
    with RoutingClient
    with FailFastCirceSupport
    with AutoCloseable
    with StrictLogging {

  // exposes our implicit materializer to mixed-in traits and stuff
  protected def haveAMaterializer = mat

  type JobResponse = Future[_ <: ClientResponse]

  type Dispatch = (String, MatchDetails, WorkerDetails) => WorkerClient

  import RestClient.implicits._

  implicit protected def execContext = mat.executionContext

  override def subscribe(request: WorkSubscription) = rest.send(ExchangeHttp(request)).flatMap(_.as[WorkSubscriptionAck])

  override def take(request: RequestWork) = rest.send(ExchangeHttp(request)).flatMap(_.as[RequestWorkAck])

  override def submit(submit: SubmitJob): JobResponse = {
    enqueueAndDispatch(submit)(_.sendRequest(submit.job))._1
  }

  private var workerClientByLocation = Map[HostLocation, Dispatch]()

  protected def clientFor(location: HostLocation): Dispatch = {
    workerClientByLocation.get(location) match {
      case Some(client) =>
        logger.debug(s"Reusing cached client at $location")
        client
      case None =>
        val newClient: Dispatch = if (location == rest.location) WorkerClient(rest) else WorkerClient(location)
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

  override def close(): Unit = rest.close()
}

object ExchangeClient {
  def apply(rest: RestClient)(implicit sys: ActorSystem, mat: Materializer): ExchangeClient = new ExchangeClient(rest)

  def apply(location: HostLocation)(implicit sys: ActorSystem, mat: Materializer): ExchangeClient = apply(RestClient(location))

  def apply(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer): ExchangeClient = apply(HostLocation(host, port))
}