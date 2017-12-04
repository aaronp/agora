package agora.api.streams

import org.reactivestreams.{Processor, Publisher, Subscriber}

/**
  * Allows something which has a publisher to act like a processor
  *
  */
trait HasProcessor[S, P] extends HasSubscriber[S] with HasPublisher[P] {

  protected def underlyingProcessor: Processor[S, P]

  override protected def underlyingPublisher: Publisher[P] = underlyingProcessor

  override protected def underlyingSubscriber: Subscriber[S] = underlyingProcessor
}
