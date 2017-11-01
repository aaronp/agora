package agora.api.exchange

import agora.api.exchange.observer.ExchangeObserver

import scala.concurrent.ExecutionContext.Implicits.global

class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer: ExchangeObserver): Exchange = {
    val exchange = Exchange(observer)(JobPredicate())
//    ServerSideExchange(exchange, observer)
    exchange
  }

}
