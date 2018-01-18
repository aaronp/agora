package agora.flow

import java.util.concurrent.atomic.AtomicInteger

/**
  * A keyed publisher which uses incrementing integer ids for keys
  * @tparam T
  */
trait IntKeyedPublisher[T] extends KeyedPublisher[T] {

  private val ids = new AtomicInteger(0)

  override type SubscriberKey = Int

  override protected def nextId() = ids.incrementAndGet()
}
