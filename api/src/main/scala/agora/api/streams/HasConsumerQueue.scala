package agora.api.streams

/**
  * Meant to be mixed in w/ a Subscriber should the subscriber wish to provide a custom queue
  *
  * @tparam T
  */
trait HasConsumerQueue[T] {

  /**
    * The queue used to enqueue messages to the subscriber as they are published but before they are delivered
    * @return the consumer queue
    */
  def consumerQueue: ConsumerQueue[T]
}
