package agora.api.streams

import java.util.concurrent.atomic.AtomicInteger

trait IntKeyedPublisher[T] extends KeyedPublisher[T] {

  private val ids = new AtomicInteger(0)

  override type SubscriberKey = Int

  override protected def nextId() = ids.incrementAndGet()
}
