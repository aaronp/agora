package jabroni.api.exchange

import akka.stream.Materializer
import jabroni.api.client._
import jabroni.api.worker.SubscriptionKey
import jabroni.api.{JobId, nextJobId, nextSubscriptionId}
import jabroni.rest.client.RestClient
import jabroni.rest.exchange.ExchangeHttp

import scala.collection.parallel.ParSeq
import scala.concurrent.{ExecutionContext, Future}

/**
  * An exchange supports both 'client' requests (e.g. offering and cancelling work to be done)
  * and work subscriptions
  */
trait Exchange extends JobScheduler {

  def pull(req: SubscriptionRequest): Future[SubscriptionResponse]

  def subscribe(req: WorkSubscription)(implicit ec: ExecutionContext) = pull(req).map(_.asInstanceOf[WorkSubscriptionAck])

  def take(req: RequestWork)(implicit ec: ExecutionContext): Future[RequestWorkAck] = pull(req).map(_.asInstanceOf[RequestWorkAck])

  def take(id: SubscriptionKey, itemsRequested: Int)(implicit ec: ExecutionContext): Future[RequestWorkAck] = take(RequestWork(id, itemsRequested))
}

object Exchange {
  def apply(implicit matcher: JobPredicate = JobPredicate()): Exchange = new InMemory

  def client(rest: RestClient)(implicit ec: ExecutionContext, mat: Materializer): Exchange = new RestExchange(rest)

  class RestExchange(rest: RestClient)(implicit ec: ExecutionContext, mat: Materializer) extends Exchange {
    import RestClient.implicits._

    override def pull(request: SubscriptionRequest): Future[SubscriptionResponse] = {
      request match {
        case subscribe: WorkSubscription =>
          rest.send(ExchangeHttp(subscribe)).flatMap(_.as[WorkSubscriptionAck])
        case take: RequestWork =>
          rest.send(ExchangeHttp(take)).flatMap(_.as[RequestWorkAck])
      }
    }

    override def send(request: ClientRequest): Future[ClientResponse] = {
      request match {
        case submit: SubmitJob => rest.send(ExchangeHttp(submit)).flatMap(_.as[SubmitJobResponse])
      }
    }
  }

  class InMemory(implicit matcher: JobPredicate) extends Exchange {
    private var subscriptionsById = Map[SubscriptionKey, WorkSubscription]()
    private var pending = Map[SubscriptionKey, Int]()
    private var jobsById = Map[JobId, SubmitJob]()

    override def pull(req: SubscriptionRequest): Future[SubscriptionResponse] = {
      req match {
        case subscription: WorkSubscription =>
          val id = nextSubscriptionId
          subscriptionsById = subscriptionsById.updated(id, subscription)
          Future.successful(WorkSubscriptionAck(id))
        case RequestWork(id, n) =>
          subscriptionsById.get(id) match {
            case None => Future.failed(new Exception(s"$id? WTF?"))
            case Some(subscription) =>
              val before = pending.getOrElse(id, 0)
              if (before == 0) {
                triggerMatch()
              }
              val total = before + n
              updatePending(id, total)
              Future.successful(RequestWorkAck(id, total))
          }
      }
    }

    private def updatePending(id: SubscriptionKey, n: Int) = pending = pending.updated(id, n)

    override def send(request: ClientRequest): Future[ClientResponse] = {
      request match {
        case job: SubmitJob =>
          val id = nextJobId
          jobsById = jobsById.updated(id, job)
          triggerMatch()
          Future.successful(SubmitJobResponse(id))
      }
    }

    def triggerMatch() = {
      val newJobs = jobsById.filter {
        case (id, job) =>

          val candidates: ParSeq[(SubscriptionKey, WorkSubscription, Int)] = pending.toSeq.par.collect {
            case (id, requested) if job.matches(subscriptionsById(id)) =>
              val subscription = subscriptionsById(id)
              (id, subscription, requested.ensuring(_ > 0) - 1)
          }

          val chosen = job.submissionDetails.selection.select(candidates.seq)
          if (chosen.isEmpty) {
            true
          } else {
            pending = chosen.foldLeft(pending) {
              case (p, (key, _, 0)) => p - key
              case (p, (key, _, n)) => p.updated(key, n)
            }
            onMatch(job, chosen)
            false // remove the job... it got sent somewhere
          }
      }
      jobsById = newJobs
    }


    protected def onMatch(job: SubmitJob, workers: Seq[(SubscriptionKey, WorkSubscription, Int)]) = {
      workers.foreach {
        case (_, sub, n) => sub.onNext(job, n)
      }
    }
  }

}