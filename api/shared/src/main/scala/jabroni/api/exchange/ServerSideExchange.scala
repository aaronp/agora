package jabroni.api.exchange

/**
  * Adds a special type for local exchanges. also exposing a means to observe jobs
  *
  * @param underlying
  * @param observer
  */
case class ServerSideExchange(underlying: Exchange, val observer: MatchObserver = MatchObserver()) extends Exchange {

  override def onClientRequest(request: ClientRequest) = underlying.onClientRequest(request)

  override def onSubscriptionRequest(req: SubscriptionRequest) = underlying.onSubscriptionRequest(req)
}
