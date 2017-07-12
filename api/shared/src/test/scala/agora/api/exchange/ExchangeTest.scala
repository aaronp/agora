package agora.api.exchange

class ExchangeTest extends ExchangeSpec {
  override def newExchange(observer : MatchObserver): Exchange = Exchange(observer)(JobPredicate())

}
