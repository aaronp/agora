package jabroni.api.exchange

import org.reactivestreams.Subscriber


/**
  * a subscription which can filter the inputs sent to it
  * (and possibly serialize itself)
  *
  * @tparam T
  */
trait FilteringSubscriber[T, C] extends Subscriber[T] {
  def subscriberData: C

  def filter: Filter[T]
}
