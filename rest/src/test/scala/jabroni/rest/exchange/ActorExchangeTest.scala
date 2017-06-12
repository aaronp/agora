package jabroni.rest.exchange

import akka.actor.ActorSystem
import jabroni.api.exchange.{Exchange, ExchangeSpec, JobPredicate, MatchObserver}

class ActorExchangeTest extends ExchangeSpec {

  override def newExchange(observer: MatchObserver): Exchange = {
    val ex = Exchange(observer)(JobPredicate())
    val sys = ActorSystem("ActorExchangeTest")
    ActorExchange(ex, sys)
  }

}
