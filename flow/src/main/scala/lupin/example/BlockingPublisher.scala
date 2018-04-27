package lupin.example

import org.reactivestreams.{Publisher, Subscriber}

/**
  * A publisher where calls to 'offer' will block until a subscriber requests elements
  */
class BlockingPublisher[T] extends Publisher[T] {
  override def subscribe(s: Subscriber[_ >: T]): Unit = {
    ???
  }
}
object BlockingPublisher {}
