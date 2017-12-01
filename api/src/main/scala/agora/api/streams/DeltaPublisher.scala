package agora.api.streams

import org.reactivestreams.Subscriber

/**
  * The DeltaPublisher consumes raw data of type T and groups it based on a field 'A'
  *
  * @tparam T
  */
class DeltaPublisher[T](publisher: BasePublisher[T]) {

  def subscribe[A](subscriber: Subscriber[_ >: T])(implicit selector: FieldSelector[T, A]) = {}
}
