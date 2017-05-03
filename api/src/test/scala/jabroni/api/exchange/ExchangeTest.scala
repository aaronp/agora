package jabroni.api.exchange

import jabroni.api.exchange.Exchange.OnMatch

class ExchangeTest extends ExchangeSpec {
  override def newExchange[T](observer : OnMatch[T]): Exchange = Exchange(observer)
}
