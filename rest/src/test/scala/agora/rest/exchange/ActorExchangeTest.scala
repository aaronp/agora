package agora.rest.exchange

import agora.api.exchange.{Exchange, ExchangeSpec, JobPredicate, MatchObserver}
import agora.rest.HasMaterializer

class ActorExchangeTest extends ExchangeSpec with HasMaterializer {

  override def newExchange(observer: MatchObserver): Exchange = {
    val ex = Exchange(observer)(JobPredicate())
    ActorExchange(ex, system)
  }

}
