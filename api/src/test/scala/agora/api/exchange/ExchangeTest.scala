package agora.api.exchange

import scala.concurrent.ExecutionContext.Implicits.global
class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer: MatchObserver): Exchange = {
    val exchange = Exchange(observer)(JobPredicate())
//    ServerSideExchange(exchange, observer)
    exchange
  }

}
