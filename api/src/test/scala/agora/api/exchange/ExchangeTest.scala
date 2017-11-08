package agora.api.exchange

import agora.api.exchange.observer.ExchangeObserver

import scala.concurrent.ExecutionContext.Implicits.global

class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer: ExchangeObserver): Exchange = {
    Exchange(observer)(JobPredicate())
  }
}
