package jabroni.api.exchange

import scala.concurrent.Future

/**
  * Represents something which can check the current state of the exchange queues.
  *
  * This is current kept separate from the Exchange signature so as
  * (1) to require/expose less about a particular exchange itself
  * (2) more practically, expose potentially separate APIs in cases such as e.g. our ui
  */
trait QueueObserver {


  def listJobs(request: QueuedJobs): Future[QueuedJobsResponse]

  final def listJobs(): Future[QueuedJobsResponse] = listJobs(new QueuedJobs())

  final def listSubscriptions(): Future[ListSubscriptionsResponse] = listSubscriptions(new ListSubscriptions())

  def listSubscriptions(request: ListSubscriptions): Future[ListSubscriptionsResponse]

}
