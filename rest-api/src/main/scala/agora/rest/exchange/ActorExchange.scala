package agora.rest.exchange

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import agora.api.exchange._
import agora.rest.exchange.ActorExchange.{WrappedClientRequest, WrappedSubscriptionRequest}
import org.slf4j.LoggerFactory

import scala.concurrent.{Future, Promise}

/**
  * Allows an exchange to be put behing an actor
  *
  * @param underlying
  */
class ActorExchange(underlying: Exchange) extends Actor {

  override def receive: Receive = {
    case WrappedClientRequest(promise, request) =>
      promise.tryCompleteWith(underlying.onClientRequest(request))
    case WrappedSubscriptionRequest(promise, request) =>
      promise.tryCompleteWith(underlying.onSubscriptionRequest(request))
  }

  override def unhandled(message: Any): Unit = {
    val msg = s"${self.path} couldn't handle $message"
    LoggerFactory.getLogger(getClass).error(msg)
    super.unhandled(message)
    sys.error(msg)
  }
}

object ActorExchange {

  /** Wraps the given exchange behind an actor, ensuring synchronous execution
    *
    * @return an actor-based exchange
    */
  def apply(exchange: Exchange, system: ActorSystem): Exchange = {
    val exchangeActor = system.actorOf(props(exchange))
    forActor(exchangeActor)
  }

  def props(exchange: Exchange): Props = Props(new ActorExchange(exchange))

  def forActor(actorExchange: ActorRef): Exchange = {
    new ActorExchangeClient(actorExchange)
  }

  private case class WrappedClientRequest(promise: Promise[ClientResponse], request: ClientRequest)

  private case class WrappedSubscriptionRequest(promise: Promise[SubscriptionResponse], request: SubscriptionRequest)

  private class ActorExchangeClient(actorExchange: ActorRef) extends Exchange {
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
  }

}
