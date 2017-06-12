package jabroni.api.exchange

class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer : MatchObserver): Exchange = Exchange(observer)(JobPredicate())
}
