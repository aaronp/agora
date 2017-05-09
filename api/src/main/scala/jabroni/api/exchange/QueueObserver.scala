package jabroni.api.exchange

import scala.concurrent.Future

/**
  * Represents something which can check the current state of the exchange queues
  */
trait QueueObserver {


  def listJobs(request: QueuedJobs): Future[QueuedJobsResponse]
  final def listJobs(): Future[QueuedJobsResponse] = listJobs(new QueuedJobs())

  final def listSubscriptions(): Future[ListSubscriptionsResponse] = listSubscriptions(new ListSubscriptions())

  def listSubscriptions(request: ListSubscriptions): Future[ListSubscriptionsResponse]

}
