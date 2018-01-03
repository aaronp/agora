package agora.api.streams

import cats.Semigroup

trait AsConsumerQueue[F[_]] {

  def newQueue[T](input: F[T]): ConsumerQueue[T]
}

object AsConsumerQueue {

  case class MaxCapacity[T](maxCapacity: Int)

  case class KeepOnly[T](keepOnly: Int)

  /** If maxCapacity and discardOverCapacity are set to None, then the queue will be a semigroup queue which conflates
    * its elements (e.g. T + T => T)
    *
    * @param maxCapacity The maximum size of the buffer used between pulling data from the publisher and dispatching it to the subscriber
    * @param discardOverCapacity determines whether we should error or silenty discard elements when the queue exceeds maxCapacity
    * @tparam T
    */
  case class QueueArgs[T : Semigroup](maxCapacity: Option[Int], discardOverCapacity: Option[Boolean]) {
    def semigroup: Semigroup[T] = Semigroup[T]
  }

  implicit object MaxCapacityAsQueue extends AsConsumerQueue[MaxCapacity] {
    override def newQueue[T](input: MaxCapacity[T]): ConsumerQueue[T] = {
      ConsumerQueue.withMaxCapacity[T](input.maxCapacity)
    }
  }
  implicit object QueueArgsAsQueue extends AsConsumerQueue[QueueArgs] {
    override def newQueue[T](args : QueueArgs[T]): ConsumerQueue[T] = {
      ConsumerQueue.newQueue[T](args.maxCapacity, args.discardOverCapacity)(args.semigroup)
    }
  }

  implicit object KeepOnlyAsQueue extends AsConsumerQueue[KeepOnly] {
    override def newQueue[T](input: KeepOnly[T]): ConsumerQueue[T] = {
      ConsumerQueue.keepLatest[T](input.keepOnly)
    }
  }
  implicit object SemigroupAsQueue extends AsConsumerQueue[Semigroup] {
    override def newQueue[T](semigroup: Semigroup[T]): ConsumerQueue[T] = {
      ConsumerQueue[T](Option.empty[T])(semigroup)
    }
  }

}
