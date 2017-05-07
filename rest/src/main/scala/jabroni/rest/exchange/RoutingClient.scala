package jabroni.rest.exchange

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.BlockingSubmitJobResponse
import jabroni.api.worker.{WorkerDetails, WorkerRedirectCoords}

import scala.concurrent.{ExecutionContext, Future}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Encoder, Json}
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange._
import jabroni.api.worker.{HostLocation, WorkerDetails, WorkerRedirectCoords}
import jabroni.rest
import jabroni.rest.client.RestClient
import jabroni.rest.worker.WorkerClient

import scala.collection.immutable
import scala.concurrent.Future


trait RoutingClient { self : ExchangeClient =>

  type WorkerResponses = Future[List[(WorkerRedirectCoords, HttpResponse)]]

  def queue(submit: SubmitJob) = sendAndRoute(submit)._2

  import RestClient.implicits._
  import RoutingClient._

  def routeMatchedJob(submitJob: SubmitJob, matchResponse: BlockingSubmitJobResponse): Future[WorkerResponses] = {

    implicit val enc: Encoder[Json] = Encoder.instance[Json](identity)
    val x: ToEntityMarshaller[Json] = marshaller[Json]

    //      val y: Future[List[Marshalling[MessageEntity]]] = x(submit.job)

    import akka.http.scaladsl.marshalling.Marshaller._
    //      val y = PredefinedToRequestMarshallers(submit.job)

//    implicit val trm : ToRequestMarshaller[Json] =
    onSubmitResponse(submitJob.job, matchResponse)
  }

  private def onSubmitResponse[T: ToRequestMarshaller](request: T, resp: BlockingSubmitJobResponse): Future[WorkerResponses] = {
    val pears: List[(WorkerRedirectCoords, WorkerDetails)] = resp.workerCoords.zip(resp.workers)
    val futures = pears.map {
      case (wdc@WorkerRedirectCoords(location, key, remaining), details) =>
        val matchDetails = MatchDetails(resp.matchId, key, resp.jobId, remaining, resp.matchEpochUTC)
        val wc = clientFor(location)
        val path = details.path.getOrElse(s"Worker ${details} hasn't specified a path")
        wc.dispatch(path, matchDetails, request).map { resp =>
          wdc -> resp
        }
    }

    Future.sequence(futures)
  }


  protected def sendAndRoute(submit: SubmitJob): (JobResponse, WorkerResponses) = {
    val httpFuture: Future[HttpResponse] = rest.send(ExchangeHttp(submit))
    if (submit.submissionDetails.awaitMatch) {
      val blockRespFuture = httpFuture.flatMap(_.as[BlockingSubmitJobResponse])
      val workerFutures = for {
        blockingResp <- blockRespFuture
        workerResponses <- routeMatchedJob(submit, blockingResp)
      } yield {
        workerResponses
      }
      workerFutures -> workerFutures
    } else {
      httpFuture.flatMap(_.as[SubmitJobResponse]) -> Future.failed(new Exception(s"awaitMatch was not specified on $submit"))
    }
  }
}

object RoutingClient {

  val strM: ToEntityMarshaller[String] = Marshaller.stringMarshaller(MediaTypes.`application/json`)
  val jsonM: Marshaller[Json, MessageEntity] = strM.compose((_:Json).noSpaces)

  val jsonMarshaller: Marshaller[Json, HttpRequest] = jsonM.map { (me: RequestEntity) =>
//    method:   HttpMethod                = HttpMethods.GET,
//    uri:      Uri                       = Uri./,
//    headers:  immutable.Seq[HttpHeader]
    val r = HttpRequest(entity = me)
    r
  }

  val trm : ToRequestMarshaller[Json] = jsonMarshaller

}