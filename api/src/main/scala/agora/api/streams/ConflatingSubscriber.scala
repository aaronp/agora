package agora.api.streams

import org.reactivestreams.Subscriber

trait ConflatingSubscriber[T] extends Subscriber[T] {

  private var last: Option[T] = None

  override def onNext(value: T) = {
    onNextWithPrevious(last, value)
    last = Option(value)
  }

  def onNextWithPrevious(previous: Option[T], value: T): Unit
}
