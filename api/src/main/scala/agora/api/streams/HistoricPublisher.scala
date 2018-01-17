package agora.api.streams

import org.reactivestreams.Subscriber

/**
  * processor which lets you subscribe to values from a particular index
  *
  * @tparam T
  */
trait HistoricProcessor[T] extends BaseProcessor[T] {

  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit

  def latestIndex: Option[Long]
}
