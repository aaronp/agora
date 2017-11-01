package agora.rest.exchange

import agora.api.exchange.observer.ExchangeObserver
import agora.api.exchange.{Exchange, ExchangeSpec, JobPredicate}
import agora.rest.HasMaterializer

class ActorExchangeTest extends ExchangeSpec with HasMaterializer {

  override def newExchange(observer: ExchangeObserver): Exchange = {
    val ex = Exchange(observer)(JobPredicate())
    ActorExchange(ex, system)
  }

}
