package jabroni.api.exchange

class ExchangeTest extends ExchangeSpec {
  override def newExchange: Exchange = Exchange {
    case (job, workers) =>
      println(s"Match: $job, $workers")
  }
}
