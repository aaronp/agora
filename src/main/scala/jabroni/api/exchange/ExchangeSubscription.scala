package jabroni.api.exchange

import org.reactivestreams.Subscription


class ExchangeSubscription[T, C](exchange: Exchange[T, C], subscriber: FilteringSubscriber[T, C]) extends Subscription {
  override def cancel(): Unit = exchange.cancel(subscriber)

  override def request(n: Long): Unit = {
    // TODO = handle longs
    exchange.take(n.toInt, subscriber)
  }
}