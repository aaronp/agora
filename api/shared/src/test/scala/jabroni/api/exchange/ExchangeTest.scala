package jabroni.api.exchange

import jabroni.api.exchange.Exchange.OnMatch

class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer : OnMatch): Exchange = Exchange(observer)
}
