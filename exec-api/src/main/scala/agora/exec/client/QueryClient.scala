package agora.exec.client

import agora.exec.events.{QueryHttp, _}
import agora.rest.client.RestClient
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Decoder
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * Interface for querying jobs based on their events/time ranges
  */
trait QueryClient {
  def query[R <: EventQueryResponse: ClassTag](request: EventQuery.Aux[R]): Future[R]

  def systemStartTimesBetween(request: StartTimesBetween): Future[StartTimesBetweenResponse] = query(request)

  def findFirst(request: FindFirst): Future[FindFirstResponse] = query(request)

  def receivedBetween(request: ReceivedBetween): Future[ReceivedBetweenResponse] = query(request)

  def startedBetween(request: StartedBetween): Future[StartedBetweenResponse] = query(request)

  def completedBetween(request: CompletedBetween): Future[CompletedBetweenResponse] = query(request)

  def notFinishedBetween(request: NotFinishedBetween): Future[NotFinishedBetweenResponse] = query(request)

  def notStartedBetween(request: NotStartedBetween): Future[NotStartedBetweenResponse] = query(request)

  def findJob(request: FindJob): Future[FindJobResponse] = query(request)
}

object QueryClient extends FailFastCirceSupport {
  def apply(rest: RestClient): QueryClient = new QueryRestClient(rest)

  class QueryRestClient(rest: RestClient) extends QueryClient {

    import RestClient.implicits._
    import rest.{executionContext, materializer}

    override def query[R <: EventQueryResponse: ClassTag](request: EventQuery.Aux[R]): Future[R] = {
      implicit val f: Decoder[EventQueryResponse] = EventQueryResponse.EventQueryResponseFormat
      val future: Future[EventQueryResponse]      = rest.send(QueryHttp.forQuery(request)).flatMap(_.as[EventQueryResponse]())

      future.mapTo[R]
    }
  }

}
