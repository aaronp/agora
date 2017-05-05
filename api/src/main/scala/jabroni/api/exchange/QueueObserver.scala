package jabroni.api.exchange

import scala.concurrent.Future

trait QueueObserver {

  def query(request: ObserverRequest): Future[ObserverResponse] = {
    request match {
      case query: QueuedJobs => listJobs(query)
      case query: ListSubscriptions => listSubscriptions(query)
    }
  }

  def listJobs(request: QueuedJobs): Future[QueuedJobsResponse] = {
    query(request).mapTo[QueuedJobsResponse]
  }
  final def listJobs(): Future[QueuedJobsResponse] = listJobs(new QueuedJobs())

  final def listSubscriptions(): Future[ListSubscriptionsResponse] = listSubscriptions(new ListSubscriptions())

  def listSubscriptions(request: ListSubscriptions): Future[ListSubscriptionsResponse] = {
    query(request).mapTo[ListSubscriptionsResponse]
  }

}
