package jabroni.domain.actors

import akka.actor.{ActorRef, Props}
import com.typesafe.scalalogging.StrictLogging
import jabroni.api.client.SubmitJob
import jabroni.api.exchange.{RequestWork, WorkSubscription}
import jabroni.api.worker.SubscriptionKey


// entry point that keeps track of offers and requests
class ExchangeActor(initialState: ExchangeActor.State) extends BaseActor {

  var state = initialState

  override def receive: Receive = {
    case msg: SubmitJob =>
    //      val props = PendingJob.State(self, msg, state.nrWorkers, state.msgId, sender())
    //      state = state.addPending(props, context.actorOf(props.props, s"job-${state.msgId}"))
    case msg: WorkSubscription =>

    case msg: RequestWork =>
    //      state.workerRefForKey(msg.key) match {
    //        case Some(worker) =>
    //          state = state.updateWorker(msg, worker, sender)
    //        case None =>
    //          val workerState = Worker.State(self, msg, state.msgId, sender())
    //          state = state.addWorker(workerState, context.actorOf(workerState.props, s"worker-${state.msgId}"))
    //      }
  }
}

object ExchangeActor {

  trait State {
//    def nrWorkers: Int

    def msgId: Long

    //    def addPending(job: PendingJob.State, ref: ActorRef): State
    //    def addWorker(worker: Worker.State, ref: ActorRef): State
    //    def updateWorker(req: RequestWork, ref: ActorRef, replyTo: ActorRef): State
    //    def workerRefForKey(id: WorkSubscription): Option[ActorRef]
  }

  case class ExchangeState(override val msgId: Long = 0,
                           pendingJobs: List[(PendingJob.State, ActorRef)] = Nil,
                           availableWorkersByKey: Map[SubscriptionKey, (Worker.State, ActorRef)] = Map.empty,
                           val nrWorkers: Long = 0) extends State with StrictLogging {
    def addPending(job: PendingJob.State, ref: ActorRef): State = {
      copy(msgId = msgId + 1, pendingJobs = (job, ref) :: pendingJobs)
    }

    //
    //    override def updateWorker(req: WorkSubscription, newWorkerRef: ActorRef, replyTo: ActorRef): State = {
    //      val (workerState, workerRef) = availableWorkersByKey(req.worker.location)
    //      if (workerRef.path != newWorkerRef.path) {
    //        logger.warn(s"Worker ${req.worker} moved from $workerRef to $newWorkerRef")
    //      }
    //      val newState = workerState.copy()
    //      val newWorkers = availableWorkersByKey.updated(req.key, (newState, newWorkerRef))
    //      copy(availableWorkersByKey = newWorkers)
    //    }
    //
    //    override def addWorker(worker: Worker.State, ref: ActorRef): State = {
    //      require(!availableWorkersByKey.contains(worker.key))
    //      val newWorkers = availableWorkersByKey.updated(worker.key, (worker, ref))
    //      copy(msgId = msgId + 1,
    //        availableWorkersByKey = newWorkers,
    //        nrWorkers = nrWorkers + 1)
    //    }

    //    override def workerRefForKey(id: SubscriptionKey) = {
    //      availableWorkersByKey.get(id).map(_._2)
    //    }
  }

  def props(initialState: State) = Props(new ExchangeActor(initialState))

}


class PendingJob[A, B](args: PendingJob.State) extends BaseActor {

  import PendingJob._
  import args._

  override def preStart(): Unit = {
    super.preStart()
    eventStream.publish(OnJobBroadcast(job, self))
  }

  override def receive = {
    case _ => ???
  }
}

object PendingJob {

  case class State(exchange: ActorRef,
                   job: SubmitJob,
                   nrWorkers: Int,
                   msgId: Long,
                   replyTo: ActorRef) {
    def props = Props(new PendingJob(this))
  }

  case class OnJobBroadcast(requestWork: SubmitJob, replyTo: ActorRef)

}


// represents a number of open requests from one worker, subscribed to the work received broadcast
class Worker(state: Worker.State) extends BaseActor {

  override def preStart(): Unit = {
    super.preStart()
    eventStream.subscribe(self, classOf[PendingJob.OnJobBroadcast])
  }

  override def receive = {
    case onJob: PendingJob.OnJobBroadcast => ???
  }
}

object Worker extends BaseActor {

  case class State(exchange: ActorRef,
                   subscription: WorkSubscription,
                   msgId: Long,
                   replyTo: ActorRef) {
    //    def incRequested(n: Int) = {
    //      copy(requestWork = requestWork.copy(itemsRequested = requestWork.itemsRequested + n))
    //    }
    //
    //    def location = requestWork..location
    //
    //    def key = requestWork.key

    def props = Props(new Worker(this))
  }

}

class MatchResult extends BaseActor {
}

object MatchResult {
}

