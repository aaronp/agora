package jabroni.api.exchange

import org.reactivestreams.Subscriber

trait ConsumerPool[T] {

  def offer(n: Long, subscriber: Subscriber[T])

  def remove(subscriber: Subscriber[T])

  def findMatching(value : T) : Iterator[(Long, Subscriber[T])]

}
