package agora.rest.exchange

import java.time.LocalDateTime
import java.util.concurrent.TimeoutException

import agora.api.exchange._
import agora.io.BaseActor
import agora.rest.exchange.ActorExchange.{StopBuffering, WithQueueState, WrappedClientRequest, WrappedSubscriptionRequest}
import akka.actor.{ActorRef, ActorSystem, Cancellable, PoisonPill, Props}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

/**
  * Allows an exchange to be put behing an actor
  *
  * @param underlying             the underlying exchange to handle the requests
  * @param bufferTimeoutThreshold the amount of time to wait for 'synchronous' operations to complete, during which time messages will be buffered internally
  */
class ActorExchange(underlying: Exchange, bufferTimeoutThreshold: FiniteDuration) extends BaseActor {

  val handleExchangeRequests: Receive = {
    case WrappedClientRequest(promise, request) =>
      promise.tryCompleteWith(underlying.onClientRequest(request))
    case WrappedSubscriptionRequest(promise, request) =>
      promise.tryCompleteWith(underlying.onSubscriptionRequest(request))
    case wqs @ WithQueueState(promise, query, _) =>
      import context.dispatcher
      val result: Future[QueueStateResponse] = underlying.queueState(query)
      val giveUpAfter                        = LocalDateTime.now().plusNanos(bufferTimeoutThreshold.toNanos)
      val scheduled: Cancellable             = context.system.scheduler.scheduleOnce(bufferTimeoutThreshold, self, StopBuffering(true))
      context.become(buffering(giveUpAfter, Nil, promise, scheduled))
      result.onComplete {
        case Success(state) =>
          wqs.completeWith(state)
          self ! StopBuffering(false)
        case Failure(err) =>
          logger.error(s"Got error when executing 'withQueueState': $err", err)
          self ! StopBuffering(false)
      }
    case StopBuffering(flag) =>
      logger.debug(s"Ignoring 'StopBuffering($flag)' request as we're not buffering...")
  }

  override def receive: Receive = handleExchangeRequests

  def buffering(giveUpAfter: LocalDateTime, requests: List[Any], withQueueStatePromise: Promise[Any], stopBufferingTimeout: Cancellable): Receive = {
    case StopBuffering(timedOut) =>
      if (timedOut) {
        val errorText = {
          val startedWaiting = LocalDateTime.now.minusNanos(bufferTimeoutThreshold.toNanos)
          s"We started handling a 'WithQueueState' message at about $startedWaiting, but have now timed out after $bufferTimeoutThreshold."
        }
        val wasAbleToFail = withQueueStatePromise.tryFailure(new TimeoutException(errorText))
        logger.error(s"$errorText ${if (wasAbleToFail) "We were able to fail the supplied promise" else "Failing the promise wasn't successful"}")
      } else {
        logger.debug(s"Told we can stop buffering ... flushing ${requests.size} messages")
      }
      stopBufferingTimeout.cancel()
      requests.reverseIterator.foreach(self ! _)
      context.become(handleExchangeRequests)
    case msg =>
      logger.debug(s"Buffering $msg")
      context.become(buffering(giveUpAfter, msg :: requests, withQueueStatePromise, stopBufferingTimeout))
  }
}

object ActorExchange {

  private case class StopBuffering(timedOut: Boolean)

  /** Wraps the given exchange behind an actor, ensuring synchronous execution
    *
    * @return an actor-based exchange
    */
  def apply(exchange: Exchange, bufferTimeoutThreshold: FiniteDuration, system: ActorSystem): ActorExchangeClient = {
    val exchangeActor = system.actorOf(props(exchange, bufferTimeoutThreshold))
    forActor(exchangeActor)
  }

  def props(exchange: Exchange, bufferTimeoutThreshold: FiniteDuration): Props = Props(new ActorExchange(exchange, bufferTimeoutThreshold))

  def forActor(actorExchange: ActorRef): ActorExchangeClient = {
    new ActorExchangeClient(actorExchange)
  }

  private case class WrappedClientRequest(promise: Promise[ClientResponse], request: ClientRequest)

  private case class WrappedSubscriptionRequest(promise: Promise[SubscriptionResponse], request: SubscriptionRequest)

  /**
    * Represents a function which will be invoked on an exchange in a thread-safe manor (so long as there is only one
    * actor handling exchange messages)
    *
    * @param promise        the result of the thunk
    * @param criteria       the criteria used to query the exchange
    * @param withQueueState a function to excute against the known, current exchange state
    * @tparam T the return value of the 'withQueueState' hunk
    */
  private case class WithQueueState[T](promise: Promise[T], criteria: QueueState, withQueueState: QueueStateResponse => T) {
    def completeWith(q: QueueStateResponse) = {
      val tri: Try[T] = Try(withQueueState(q))
      promise.tryComplete(tri)
    }
  }

  /**
    * Delegates to the underlying 'exchange' represented by the given ActorRef
    *
    * @param actorExchange the actor representing an exchange
    */
  class ActorExchangeClient(actorExchange: ActorRef) extends Exchange with AutoCloseable {

    /**
      * When creating a new 'observer', we need to be able to know the 'state of the world' (SOW) to send, followed by
      * all the events which happen after the state is sent.
      *
      * That means observer subscriptions need to be single-threaded, which is what a 'ActorExchangeClient'
      *
      * @param query
      * @return
      */
    def withQueueState[T](query: QueueState = QueueState())(thunk: QueueStateResponse => T): Future[T] = {
      val promise = Promise[T]()
      actorExchange ! WithQueueState(promise, query, thunk)
      promise.future
    }

    override def onSubscriptionRequest(request: SubscriptionRequest): Future[SubscriptionResponse] = {
      val promise = Promise[SubscriptionResponse]()
      actorExchange ! WrappedSubscriptionRequest(promise, request)
      promise.future
    }

    override def onClientRequest(request: ClientRequest): Future[ClientResponse] = {
      val promise = Promise[ClientResponse]()
      actorExchange ! WrappedClientRequest(promise, request)
      promise.future
    }

    override def close(): Unit = actorExchange ! PoisonPill
  }

}
